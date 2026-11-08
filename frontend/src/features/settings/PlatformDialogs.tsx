import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
  Group,
  Loader,
  Modal,
  PasswordInput,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconHistory,
} from '@tabler/icons-react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listPlatformEvents,
  updatePlatform,
} from '../../lib/api/endpoints/platforms';
import type {
  PlatformCatalogEntry,
  PlatformConnection,
  UpdatePlatformInput,
} from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { formatDateTime } from '../../lib/format/date';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { ErrorState } from '../../components/feedback/ErrorState';
import {
  actorLabel,
  changeTitle,
  describePlatformChange,
} from './platformHistory';
import { mapPlatformSetupErrors } from './platformSetup';

const HISTORY_PAGE_SIZE = 10;

function platformName(
  connection: PlatformConnection,
  entry: PlatformCatalogEntry | null,
): string {
  return entry?.displayName ?? connection.displayName;
}

function useSaveConnection(connection: PlatformConnection, onSaved: () => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdatePlatformInput) => updatePlatform(connection.id, input),
    onSuccess: async (saved) => {
      queryClient.setQueryData<PlatformConnection[]>(queryKeys.platforms, (current) =>
        current?.map((entry) => (entry.id === saved.id ? saved : entry)),
      );
      await queryClient.invalidateQueries({
        queryKey: queryKeys.platformEvents(connection.id),
      });
      onSaved();
    },
  });
}

export function EditPlatformModal({
  connection,
  entry,
  onClose,
}: {
  connection: PlatformConnection;
  entry: PlatformCatalogEntry | null;
  onClose: () => void;
}) {
  const [displayName, setDisplayName] = useState(connection.displayName);
  const [apiEndpoint, setApiEndpoint] = useState(connection.apiEndpoint);
  const [webhookSecret, setWebhookSecret] = useState('');
  const [credentials, setCredentials] = useState<Record<string, string>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);

  const save = useSaveConnection(connection, () => {
    notifications.show({
      title: 'Connection updated',
      message: 'Your changes are saved.',
      color: 'emerald',
      icon: <IconCheck size={18} />,
    });
    onClose();
  });

  const credentialFields = entry?.credentialFields ?? [];

  const submit = () => {
    setError(null);
    const nextErrors: Record<string, string> = {};
    if (!displayName.trim()) {
      nextErrors.displayName = 'Give this connection a name so you can tell it apart.';
    }
    if (!apiEndpoint.trim()) {
      nextErrors.apiEndpoint = 'Enter the API address.';
    }
    if (webhookSecret.trim() && webhookSecret.trim().length < 16) {
      nextErrors.webhookSecret = 'The signing secret must be at least 16 characters.';
    }
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const input: UpdatePlatformInput = {
      version: connection.version,
      displayName: displayName.trim(),
      apiEndpoint: apiEndpoint.trim(),
    };
    if (webhookSecret.trim()) input.webhookSecret = webhookSecret.trim();
    for (const field of credentialFields) {
      const value = credentials[field.key]?.trim();
      if (value) input[field.key] = value;
    }

    save.mutate(input, {
      onError: (caught) => {
        setError(caught);
        setFieldErrors(mapPlatformSetupErrors(caught, entry));
      },
    });
  };

  return (
    <Modal
      opened
      onClose={() => {
        if (!save.isPending) onClose();
      }}
      title={`Edit ${platformName(connection, entry)}`}
      size="md"
    >
      <Stack gap="md">
        <ProblemAlert error={error} title="Could not save changes" compact />

        <TextInput
          label="Display name"
          description="A name you will recognise, for example “Northwind Supply EU”."
          withAsterisk
          aria-required="true"
          value={displayName}
          onChange={(event) => {
            setDisplayName(event.currentTarget.value);
            setFieldErrors((current) => ({ ...current, displayName: '' }));
          }}
          error={fieldErrors.displayName}
        />

        <TextInput
          label="API address"
          description="Where StockLedger reads this channel's orders."
          withAsterisk
          aria-required="true"
          value={apiEndpoint}
          onChange={(event) => {
            setApiEndpoint(event.currentTarget.value);
            setFieldErrors((current) => ({ ...current, apiEndpoint: '' }));
          }}
          error={fieldErrors.apiEndpoint}
        />

        <PasswordInput
          label="Webhook secret"
          description="Leave blank to keep the current signing secret."
          value={webhookSecret}
          onChange={(event) => {
            setWebhookSecret(event.currentTarget.value);
            setFieldErrors((current) => ({ ...current, webhookSecret: '' }));
          }}
          error={fieldErrors.webhookSecret}
        />

        {credentialFields.map((field) => (
          <PasswordInput
            key={field.key}
            label={field.label}
            description={`Leave blank to keep the current ${field.label.toLowerCase()}.`}
            value={credentials[field.key] ?? ''}
            onChange={(event) => {
              const value = event.currentTarget.value;
              setCredentials((current) => ({ ...current, [field.key]: value }));
              setFieldErrors((current) => ({ ...current, [field.key]: '' }));
            }}
            error={fieldErrors[field.key]}
          />
        ))}

        <TextInput
          label="Webhook address"
          description="This address is the identity of the connection and cannot be changed. Create a new connection if the channel needs a different address."
          value={connection.webhookPath}
          readOnly
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button onClick={submit} loading={save.isPending}>
            Save changes
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

export function PausePlatformModal({
  connection,
  entry,
  onClose,
}: {
  connection: PlatformConnection;
  entry: PlatformCatalogEntry | null;
  onClose: () => void;
}) {
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const save = useSaveConnection(connection, () => {
    notifications.show({
      title: 'Connection paused',
      message: 'Orders from this channel will not be imported until you resume it.',
      color: 'emerald',
      icon: <IconCheck size={18} />,
    });
    onClose();
  });

  const submit = () => {
    setError(null);
    if (!reason.trim()) {
      setReasonError(
        'Give a reason for pausing this connection. It is recorded in the connection history.',
      );
      return;
    }
    setReasonError(null);
    save.mutate(
      {
        version: connection.version,
        active: false,
        pauseReason: reason.trim(),
      },
      { onError: setError },
    );
  };

  return (
    <Modal
      opened
      onClose={() => {
        if (!save.isPending) onClose();
      }}
      title={`Pause ${platformName(connection, entry)}`}
      size="sm"
    >
      <Stack gap="md">
        <ProblemAlert error={error} title="Could not pause this connection" compact />

        <Alert color="gray" variant="light" icon={<IconAlertTriangle size={18} />}>
          <Text size="sm">
            Pausing stops new orders from this channel. Orders already imported stay in your
            queues, and you can resume the connection at any time.
          </Text>
        </Alert>

        <Textarea
          label="Reason"
          description="Shown on the connection card and kept in the history."
          placeholder="For example: store closed for a seasonal break."
          withAsterisk
          aria-required="true"
          minRows={3}
          maxLength={500}
          value={reason}
          onChange={(event) => {
            setReason(event.currentTarget.value);
            setReasonError(null);
          }}
          error={reasonError}
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button color="red" onClick={submit} loading={save.isPending}>
            Pause connection
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

export function ResumePlatformModal({
  connection,
  entry,
  onClose,
}: {
  connection: PlatformConnection;
  entry: PlatformCatalogEntry | null;
  onClose: () => void;
}) {
  const [error, setError] = useState<unknown>(null);

  const save = useSaveConnection(connection, () => {
    notifications.show({
      title: 'Connection resumed',
      message: 'Orders from this channel will be imported again.',
      color: 'emerald',
      icon: <IconCheck size={18} />,
    });
    onClose();
  });

  return (
    <Modal
      opened
      onClose={() => {
        if (!save.isPending) onClose();
      }}
      title={`Resume ${platformName(connection, entry)}`}
      size="sm"
    >
      <Stack gap="md">
        <ProblemAlert error={error} title="Could not resume this connection" compact />

        <Text size="sm">
          Orders will start importing again at {connection.webhookPath}. If another active
          connection is using that address, pause it first.
        </Text>

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button
            onClick={() =>
              save.mutate(
                { version: connection.version, active: true },
                { onError: setError },
              )
            }
            loading={save.isPending}
          >
            Resume connection
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

export function PlatformHistoryModal({
  connection,
  entry,
  onClose,
}: {
  connection: PlatformConnection;
  entry: PlatformCatalogEntry | null;
  onClose: () => void;
}) {
  const history = useInfiniteQuery({
    queryKey: queryKeys.platformEvents(connection.id),
    queryFn: ({ pageParam }) =>
      listPlatformEvents(connection.id, pageParam, HISTORY_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.pagination?.hasMore ? lastPage.pagination.page + 1 : undefined,
  });

  const events = history.data?.pages.flatMap((page) => page.data) ?? [];

  return (
    <Modal
      opened
      onClose={onClose}
      title={`History for ${platformName(connection, entry)}`}
      size="md"
    >
      <Stack gap="sm">
        <Group gap="xs">
          <IconHistory size={16} />
          <Text size="sm" c="dimmed">
            Every change is recorded with who made it and when. Secrets and tokens are never
            shown.
          </Text>
        </Group>

        {history.isError ? (
          <ErrorState error={history.error} onRetry={() => void history.refetch()} />
        ) : history.isLoading ? (
          <Group>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading history…
            </Text>
          </Group>
        ) : events.length === 0 ? (
          <Text size="sm" c="dimmed">
            No changes recorded yet.
          </Text>
        ) : (
          <Stack gap={0}>
            {events.map((event, index) => (
              <Box key={event.id}>
                {index > 0 ? <Divider my="sm" /> : null}
                <Text fw={600} size="sm">
                  {changeTitle(event.changeType)}
                </Text>
                <Text size="sm">{describePlatformChange(event)}</Text>
                <Text size="xs" c="dimmed" mt={2}>
                  {actorLabel(event)} · {formatDateTime(event.occurredAt)}
                </Text>
              </Box>
            ))}
          </Stack>
        )}

        {history.hasNextPage ? (
          <Group justify="center">
            <Button
              variant="default"
              size="xs"
              onClick={() => void history.fetchNextPage()}
              loading={history.isFetchingNextPage}
            >
              Show older changes
            </Button>
          </Group>
        ) : null}
      </Stack>
    </Modal>
  );
}
