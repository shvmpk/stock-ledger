import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Group,
  Loader,
  Menu,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconDots,
  IconEdit,
  IconHistory,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlugConnected,
  IconPlus,
  IconRefresh,
  IconWorld,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getPlatformCatalog, listPlatforms, syncPlatform, testPlatform } from '../../lib/api/endpoints/platforms';
import type { PlatformConnection, PlatformSyncStatus } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { CopyableCode } from '../../components/data/CopyableCode';
import { formatDateTime } from '../../lib/format/date';
import { formatQuantity } from '../../lib/format/quantity';
import {
  EditPlatformModal,
  PausePlatformModal,
  PlatformHistoryModal,
  ResumePlatformModal,
} from './PlatformDialogs';
import {
  describeInboundStatus,
  testOutcomeColor,
  testOutcomeLabel,
} from './platformTest';

const statusConfig: Record<PlatformSyncStatus, { color: string; label: string }> = {
  SUCCESS: { color: 'green', label: 'Last sync succeeded' },
  FAILED: { color: 'red', label: 'Last sync failed' },
  RUNNING: { color: 'blue', label: 'Syncing' },
  PENDING: { color: 'gray', label: 'Not synced yet' },
};

function statusOf(connection: PlatformConnection): PlatformSyncStatus {
  return (connection.syncStatus as PlatformSyncStatus) ?? 'PENDING';
}

function joinChannelNames(names: string[]): string {
  if (names.length === 0) return 'a sales channel';
  if (names.length === 1) return names[0];
  return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

export function PlatformsPage() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.platforms, queryFn: listPlatforms });
  const catalogQuery = useQuery({
    queryKey: queryKeys.platformCatalog,
    queryFn: getPlatformCatalog,
    staleTime: Infinity,
  });
  const catalogByType = useMemo(
    () => new Map((catalogQuery.data ?? []).map((entry) => [entry.platformType, entry])),
    [catalogQuery.data],
  );
  const connectableChannelNames = (catalogQuery.data ?? [])
    .filter((entry) => entry.supported)
    .map((entry) => entry.displayName);
  const [error, setError] = useState<unknown>(null);
  const [editTarget, setEditTarget] = useState<PlatformConnection | null>(null);
  const [pauseTarget, setPauseTarget] = useState<PlatformConnection | null>(null);
  const [resumeTarget, setResumeTarget] = useState<PlatformConnection | null>(null);
  const [historyTarget, setHistoryTarget] = useState<PlatformConnection | null>(null);

  const syncMutation = useMutation({
    mutationFn: syncPlatform,
    onSuccess: async (result) => {
      queryClient.setQueryData<PlatformConnection[]>(queryKeys.platforms, (current) =>
        current?.map((connection) =>
          connection.id === result.connectionId
            ? {
                ...connection,
                syncStatus: result.status,
                lastSyncAt: result.lastSyncAt,
                lastErrorMessage: result.lastErrorMessage,
              }
            : connection,
        ),
      );
      setError(null);
      notifications.show({
        title: result.status === 'SUCCESS' ? 'Platform sync finished' : 'Platform sync failed',
        message:
          result.status === 'SUCCESS'
            ? 'Imported orders are now in the New or Waiting for stock queues.'
            : result.lastErrorMessage ?? 'The platform rejected the sync. Review the connection.',
        color: result.status === 'SUCCESS' ? 'emerald' : 'red',
        icon: result.status === 'SUCCESS' ? <IconCheck size={18} /> : <IconAlertTriangle size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.platforms }),
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
      ]);
    },
    onError: (caught) => setError(caught),
  });

  const testMutation = useMutation({
    mutationFn: testPlatform,
    onSuccess: (result) => {
      queryClient.setQueryData<PlatformConnection[]>(queryKeys.platforms, (current) =>
        current?.map((connection) =>
          connection.id === result.connectionId
            ? {
                ...connection,
                lastTestedAt: result.testedAt,
                lastTestOutcome: result.outcome,
                lastTestHttpStatus: result.httpStatus,
                lastTestDetail: result.message,
                lastInboundOrderAt: result.lastInboundOrderAt ?? connection.lastInboundOrderAt,
              }
            : connection,
        ),
      );

      const finished = result.outcome === 'SUCCESS' || result.outcome === 'AWAITING_INBOUND';
      notifications.show({
        title: finished ? 'Connection test finished' : 'Connection test failed',
        message: 'The result is shown on the connection card.',
        color:
          result.outcome === 'SUCCESS'
            ? 'emerald'
            : result.outcome === 'AWAITING_INBOUND'
              ? 'gray'
              : 'red',
        icon:
          result.outcome === 'SUCCESS' ? (
            <IconCheck size={18} />
          ) : result.outcome === 'AWAITING_INBOUND' ? (
            <IconPlugConnected size={18} />
          ) : (
            <IconAlertTriangle size={18} />
          ),
      });
    },
    onError: (caught) => setError(caught),
  });

  const connections = query.data ?? [];
  const activeCount = connections.filter((connection) => connection.active).length;
  const successCount = connections.filter((connection) => statusOf(connection) === 'SUCCESS').length;
  const failedCount = connections.filter((connection) => statusOf(connection) === 'FAILED').length;

  return (
    <>
      <PageHeader
        title="Platforms"
        description="Monitor automated order synchronization and connection health for your online sales channels."
        actions={
          <>
            <Button
              component={Link}
              to="/settings/platforms/new"
              leftSection={<IconPlus size={16} />}
            >
              Add platform
            </Button>
            <Button
              variant="default"
              leftSection={<IconRefresh size={16} />}
              onClick={() => void query.refetch()}
              loading={query.isFetching}
            >
              Refresh
            </Button>
          </>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="Platform connection summary">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(connections.length)}</Text>
          <Text className="sl-summary-label">Connections</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(activeCount)}</Text>
          <Text className="sl-summary-label">Active</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(successCount)}</Text>
          <Text className="sl-summary-label">Last sync succeeded</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value" c={failedCount > 0 ? 'var(--sl-color-danger)' : undefined}>
            {formatQuantity(failedCount)}
          </Text>
          <Text className="sl-summary-label">Needs attention</Text>
        </Box>
      </Box>

      {error ? <ProblemAlert error={error} title="Platform sync failed" /> : null}

      {query.isError || catalogQuery.isError ? (
        <ErrorState
          error={query.error ?? catalogQuery.error}
          onRetry={() => {
            void query.refetch();
            void catalogQuery.refetch();
          }}
        />
      ) : query.isLoading || catalogQuery.isLoading ? (
        <Group>
          <Loader size="sm" />
          <Text size="sm" c="dimmed">
            Loading platform connections…
          </Text>
        </Group>
      ) : connections.length === 0 ? (
        <EmptyState
          title="No platform connections"
          description={`Connect ${joinChannelNames(connectableChannelNames)} to import orders automatically.`}
          icon={<IconPlugConnected size={22} />}
          action={
            <Button
              component={Link}
              to="/settings/platforms/new"
              leftSection={<IconPlus size={16} />}
            >
              Connect your first platform
            </Button>
          }
        />
      ) : (
        <Stack gap="md">
          {connections.map((connection) => {
            const status = statusOf(connection);
            const config = statusConfig[status];
            const syncing = syncMutation.isPending && syncMutation.variables === connection.id;
            const testing = testMutation.isPending && testMutation.variables === connection.id;
            const catalogueEntry = catalogByType.get(connection.platformType);
            const platformName = catalogueEntry?.displayName ?? connection.displayName;
            const merchantLabel =
              catalogueEntry && connection.displayName && connection.displayName !== catalogueEntry.displayName
                ? connection.displayName
                : null;
            return (
              <Card key={connection.id} withBorder padding="md" radius="md">
                <Stack gap="sm">
                  <Group justify="space-between" align="flex-start" wrap="wrap">
                    <Group gap="sm" align="flex-start" wrap="nowrap">
                      <Box
                        aria-hidden="true"
                        style={{
                          width: 38,
                          height: 38,
                          borderRadius: 8,
                          display: 'grid',
                          placeItems: 'center',
                          background: 'var(--mantine-color-emerald-0)',
                          color: 'var(--mantine-color-emerald-8)',
                        }}
                      >
                        <IconWorld size={20} />
                      </Box>
                      <Box>
                        <Group gap="xs">
                          <Title order={2} size="h5">
                            {platformName}
                          </Title>
                          {merchantLabel ? (
                            <Badge variant="subtle" color="gray" size="sm">
                              {merchantLabel}
                            </Badge>
                          ) : null}
                          <Badge color={connection.active ? 'green' : 'gray'} variant="light" size="sm">
                            {connection.active ? 'Active' : 'Inactive'}
                          </Badge>
                          <Badge color={config.color} variant="light" size="sm">
                            {config.label}
                          </Badge>
                        </Group>
                        <Text size="xs" c="dimmed" mt={2}>
                          Last sync {formatDateTime(connection.lastSyncAt)}
                        </Text>
                      </Box>
                    </Group>
                    <Group gap="xs">
                      <Button
                        size="xs"
                        variant="light"
                        leftSection={<IconRefresh size={14} />}
                        loading={syncing}
                        disabled={!connection.active || !connection.syncSupported || syncing}
                        onClick={() => {
                          setError(null);
                          syncMutation.mutate(connection.id);
                        }}
                      >
                        Sync now
                      </Button>
                      <Menu shadow="md" width={220} position="bottom-end">
                        <Menu.Target>
                          <ActionIcon
                            variant="subtle"
                            color="gray"
                            aria-label={`Actions for ${platformName}`}
                          >
                            <IconDots size={18} />
                          </ActionIcon>
                        </Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Item
                            leftSection={<IconEdit size={14} />}
                            onClick={() => setEditTarget(connection)}
                          >
                            Edit connection
                          </Menu.Item>
                          <Menu.Item
                            leftSection={<IconPlugConnected size={14} />}
                            disabled={testing}
                            onClick={() => testMutation.mutate(connection.id)}
                          >
                            Test connection
                          </Menu.Item>
                          {connection.active ? (
                            <Menu.Item
                              leftSection={<IconPlayerPause size={14} />}
                              onClick={() => setPauseTarget(connection)}
                            >
                              Pause connection
                            </Menu.Item>
                          ) : (
                            <Menu.Item
                              leftSection={<IconPlayerPlay size={14} />}
                              onClick={() => setResumeTarget(connection)}
                            >
                              Resume connection
                            </Menu.Item>
                          )}
                          <Menu.Item
                            leftSection={<IconHistory size={14} />}
                            onClick={() => setHistoryTarget(connection)}
                          >
                            View history
                          </Menu.Item>
                        </Menu.Dropdown>
                      </Menu>
                    </Group>
                  </Group>

                  <Group gap="lg" wrap="wrap">
                    <Box>
                      <Text size="xs" c="dimmed">
                        API endpoint
                      </Text>
                      <CopyableCode value={connection.apiEndpoint} label="API endpoint" />
                    </Box>
                    <Box>
                      <Text size="xs" c="dimmed">
                        Webhook path
                      </Text>
                      <CopyableCode value={connection.webhookPath} label="Webhook path" />
                    </Box>
                  </Group>

                  <Group gap="xs" wrap="wrap">
                    <Badge color={connection.credentialsConfigured ? 'green' : 'amber'} variant="light" size="sm">
                      {connection.credentialsConfigured ? 'Credentials configured' : 'Credentials missing'}
                    </Badge>
                    <Badge color={connection.webhookConfigured ? 'green' : 'amber'} variant="light" size="sm">
                      {connection.webhookConfigured ? 'Webhook secret configured' : 'Webhook secret missing'}
                    </Badge>
                    {!connection.syncSupported ? (
                      <Badge color="gray" variant="light" size="sm">
                        Inbound webhook only
                      </Badge>
                    ) : null}
                    {!connection.active && connection.deactivatedAt ? (
                      <Badge color="gray" variant="light" size="sm">
                        Paused {formatDateTime(connection.deactivatedAt)}
                      </Badge>
                    ) : null}
                  </Group>

                  {!connection.active && connection.pauseReason ? (
                    <Alert
                      color="gray"
                      variant="light"
                      icon={<IconPlayerPause size={16} />}
                      title="Paused"
                    >
                      <Text size="sm">{connection.pauseReason}</Text>
                    </Alert>
                  ) : null}

                  {!connection.syncSupported ? (
                    <Text size="xs" c="dimmed">
                      {describeInboundStatus(connection.lastInboundOrderAt)}
                    </Text>
                  ) : null}

                  {connection.lastTestOutcome ? (
                    <Alert
                      color={testOutcomeColor(connection.lastTestOutcome)}
                      variant="light"
                      icon={
                        connection.lastTestOutcome === 'SUCCESS' ? (
                          <IconCheck size={16} />
                        ) : connection.lastTestOutcome === 'AWAITING_INBOUND' ? (
                          <IconPlugConnected size={16} />
                        ) : (
                          <IconAlertTriangle size={16} />
                        )
                      }
                      title={testOutcomeLabel(connection.lastTestOutcome)}
                    >
                      {connection.lastTestDetail ? (
                        <Text size="sm">{connection.lastTestDetail}</Text>
                      ) : null}
                      {connection.lastTestedAt ? (
                        <Text size="xs" c="dimmed" mt={4}>
                          Tested {formatDateTime(connection.lastTestedAt)}
                        </Text>
                      ) : null}
                    </Alert>
                  ) : null}

                  {connection.lastErrorMessage ? (
                    <Alert color="red" variant="light" icon={<IconAlertTriangle size={16} />} title="Last error">
                      <Text size="sm">{connection.lastErrorMessage}</Text>
                    </Alert>
                  ) : null}

                  <Text size="xs" c="dimmed">
                    Imported orders enter the New queue when stock can be reserved, or Waiting for stock
                    when allocation has to wait for a supplier receipt.
                  </Text>
                </Stack>
              </Card>
            );
          })}
        </Stack>
      )}

      <Text size="xs" c="dimmed" mt="md">
        Inbound webhooks cryptographically verify HMAC-SHA256 signatures before reserving inventory.
      </Text>

      {editTarget ? (
        <EditPlatformModal
          connection={editTarget}
          entry={catalogByType.get(editTarget.platformType) ?? null}
          onClose={() => setEditTarget(null)}
        />
      ) : null}
      {pauseTarget ? (
        <PausePlatformModal
          connection={pauseTarget}
          entry={catalogByType.get(pauseTarget.platformType) ?? null}
          onClose={() => setPauseTarget(null)}
        />
      ) : null}
      {resumeTarget ? (
        <ResumePlatformModal
          connection={resumeTarget}
          entry={catalogByType.get(resumeTarget.platformType) ?? null}
          onClose={() => setResumeTarget(null)}
        />
      ) : null}
      {historyTarget ? (
        <PlatformHistoryModal
          connection={historyTarget}
          entry={catalogByType.get(historyTarget.platformType) ?? null}
          onClose={() => setHistoryTarget(null)}
        />
      ) : null}
    </>
  );
}
