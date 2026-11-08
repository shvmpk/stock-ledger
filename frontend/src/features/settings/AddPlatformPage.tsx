import { useRef, useState, type FormEvent } from 'react';
import {
  ActionIcon,
  Alert,
  Box,
  Button,
  Card,
  Collapse,
  Group,
  Loader,
  PasswordInput,
  Radio,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import {
  IconAdjustments,
  IconAlertTriangle,
  IconCheck,
  IconChevronDown,
  IconEye,
  IconEyeOff,
  IconKey,
  IconPlugConnected,
} from '@tabler/icons-react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createPlatform, getPlatformCatalog, newIdempotencyKey } from '../../lib/api/endpoints/platforms';
import type {
  CreatePlatformInput,
  PlatformConnection,
  PlatformType,
} from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { CopyableCode } from '../../components/data/CopyableCode';
import {
  defaultSlugOf,
  generateWebhookSecret,
  mapPlatformSetupErrors,
  slugifyWebhookAddress,
  validatePlatformSetup,
  webhookPathFor,
  webhookUrlFor,
  type PlatformSetupValues,
} from './platformSetup';

const EMPTY_VALUES: PlatformSetupValues = {
  platformType: null,
  displayName: '',
  apiEndpoint: '',
  webhookSecret: '',
  credentials: {},
};

function credentialHint(key: string): string | undefined {
  switch (key) {
    case 'accessToken':
      return 'Shopify admin → Apps and sales channels → Develop apps → API credentials.';
    case 'consumerKey':
    case 'consumerSecret':
      return 'WooCommerce → Settings → Advanced → REST API.';
    default:
      return undefined;
  }
}

export function AddPlatformPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const catalogQuery = useQuery({
    queryKey: queryKeys.platformCatalog,
    queryFn: getPlatformCatalog,
    staleTime: Infinity,
  });

  const [values, setValues] = useState<PlatformSetupValues>(EMPTY_VALUES);
  const [customAddress, setCustomAddress] = useState('');
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [secretVisible, setSecretVisible] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);
  const [created, setCreated] = useState<{ connection: PlatformConnection; secret: string } | null>(
    null,
  );
  const requestKey = useRef(newIdempotencyKey());

  const catalog = catalogQuery.data ?? [];
  const entry =
    values.platformType === null
      ? null
      : (catalog.find((candidate) => candidate.platformType === values.platformType) ?? null);
  const previewPath = entry ? webhookPathFor(entry, values.displayName, customAddress) : null;

  const createMutation = useMutation({
    mutationFn: (input: CreatePlatformInput) => createPlatform(input, requestKey.current),
    onSuccess: async (connection) => {
      setCreated({ connection, secret: values.webhookSecret });
      setError(null);
      queryClient.setQueryData<PlatformConnection[]>(queryKeys.platforms, (current) => {
        if (!current) return [connection];
        return current.some((existing) => existing.id === connection.id)
          ? current
          : [...current, connection];
      });
      await queryClient.invalidateQueries({ queryKey: queryKeys.platforms });
    },
    onError: (caught) => {
      const mapped = mapPlatformSetupErrors(caught, entry);
      setFieldErrors(mapped);
      if (mapped.slug) setAdvancedOpen(true);
      setError(Object.keys(mapped).length > 0 ? null : caught);
    },
  });

  const choosePlatform = (value: string) => {
    setValues((current) => ({
      ...current,
      platformType: value as PlatformType,
      credentials: {},
    }));
    setFieldErrors({});
    setError(null);
  };

  const updateCredential = (key: string, value: string) => {
    setValues((current) => ({
      ...current,
      credentials: { ...current.credentials, [key]: value },
    }));
  };

  const generateSecret = () => {
    setValues((current) => ({ ...current, webhookSecret: generateWebhookSecret() }));
    setSecretVisible(true);
    setFieldErrors((current) => {
      const next = { ...current };
      delete next.webhookSecret;
      return next;
    });
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (createMutation.isPending) return;

    const errors = validatePlatformSetup(values, entry);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError(null);
      return;
    }
    if (!entry || !values.platformType) return;

    setError(null);
    const credentialValues: Record<string, string> = {};
    for (const field of entry.credentialFields) {
      credentialValues[field.key] = values.credentials[field.key]?.trim() ?? '';
    }
    const customSlug = slugifyWebhookAddress(customAddress);
    createMutation.mutate({
      platformType: values.platformType,
      displayName: values.displayName.trim(),
      apiEndpoint: values.apiEndpoint.trim(),
      webhookSecret: values.webhookSecret,
      ...(customSlug ? { slug: customSlug } : {}),
      ...(credentialValues.accessToken ? { accessToken: credentialValues.accessToken } : {}),
      ...(credentialValues.consumerKey ? { consumerKey: credentialValues.consumerKey } : {}),
      ...(credentialValues.consumerSecret
        ? { consumerSecret: credentialValues.consumerSecret }
        : {}),
    });
  };

  if (created) {
    const platformName =
      catalog.find((candidate) => candidate.platformType === created.connection.platformType)
        ?.displayName ?? created.connection.displayName;

    return (
      <>
        <PageHeader
          title="Platform connected"
          description="Finish the setup in your sales channel while the details are on screen."
        />
        <Card
          withBorder
          padding="lg"
          radius="md"
          role="region"
          aria-label="Connection details"
        >
          <Stack gap="md">
            <Alert
              color="green"
              variant="light"
              icon={<IconCheck size={18} />}
              title={`${platformName} is connected`}
            >
              StockLedger is ready to receive orders from this channel.
            </Alert>

            <Box>
              <Text size="xs" c="dimmed">
                Webhook URL
              </Text>
              <CopyableCode
                value={webhookUrlFor(created.connection.webhookPath)}
                label="Webhook URL"
              />
            </Box>

            <Box>
              <Text size="xs" c="dimmed">
                Signing secret
              </Text>
              <CopyableCode value={created.secret} label="Signing secret" />
            </Box>

            <Alert
              color="amber"
              variant="light"
              icon={<IconAlertTriangle size={18} />}
              title="Copy the signing secret now"
            >
              <Text size="sm">
                The signing secret cannot be shown again after you leave this page. Store it in{' '}
                {platformName} or your password manager.
              </Text>
            </Alert>

            <Text size="sm" c="dimmed">
              In {platformName}, add this URL as the order webhook and use the signing secret as
              the signing key. Then place a test order to confirm the connection.
            </Text>

            <Group justify="flex-end">
              <Button onClick={() => navigate('/settings/platforms')}>Done</Button>
            </Group>
          </Stack>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Connect a platform"
        description="Add a sales channel so its orders arrive in StockLedger. You can pause it at any time."
        actions={
          <Button variant="default" component={Link} to="/settings/platforms">
            Back to platforms
          </Button>
        }
      />

      {catalogQuery.isError ? (
        <ErrorState
          error={catalogQuery.error}
          onRetry={() => {
            void catalogQuery.refetch();
          }}
        />
      ) : catalogQuery.isLoading ? (
        <Group>
          <Loader size="sm" />
          <Text size="sm" c="dimmed">
            Loading the sales channels…
          </Text>
        </Group>
      ) : (
        <Card withBorder padding="lg" radius="md">
          <form onSubmit={handleSubmit}>
            <Stack gap="lg">
              <Radio.Group
                value={values.platformType ?? ''}
                onChange={choosePlatform}
                label="Sales channel"
                description="Choose where your orders come from."
                withAsterisk
                aria-required="true"
                error={fieldErrors.platformType}
              >
                <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm" mt="xs">
                  {catalog.map((candidate) => (
                    <Radio
                      key={candidate.platformType}
                      value={candidate.platformType}
                      label={candidate.displayName}
                      description={candidate.supported ? undefined : candidate.unsupportedReason}
                      disabled={!candidate.supported}
                    />
                  ))}
                </SimpleGrid>
              </Radio.Group>

              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                <TextInput
                  label="Connection name"
                  description="A name you will recognise, for example “Northwind Supply EU”."
                  placeholder={entry ? `Northwind Supply — ${entry.displayName}` : 'Northwind Supply'}
                  withAsterisk
                  aria-required="true"
                  value={values.displayName}
                  onChange={(event) => {
                    const { value } = event.currentTarget;
                    setValues((current) => ({ ...current, displayName: value }));
                  }}
                  error={fieldErrors.displayName}
                />
                <TextInput
                  label="API address"
                  description={
                    entry ? (
                      <>
                        Example: <code>{entry.apiEndpointExample}</code>
                      </>
                    ) : (
                      'The address of your sales channel.'
                    )
                  }
                  placeholder={entry?.apiEndpointExample}
                  withAsterisk
                  aria-required="true"
                  value={values.apiEndpoint}
                  onChange={(event) => {
                    const { value } = event.currentTarget;
                    setValues((current) => ({ ...current, apiEndpoint: value }));
                  }}
                  error={fieldErrors.apiEndpoint}
                />
              </SimpleGrid>

              {entry && entry.credentialFields.length > 0 ? (
                <Stack gap="md">
                  <Title order={2} size="h5">
                    API credentials
                  </Title>
                  {entry.credentialFields.map((field) => (
                    <PasswordInput
                      key={field.key}
                      label={field.label}
                      description={credentialHint(field.key)}
                      withAsterisk
                      aria-required="true"
                      value={values.credentials[field.key] ?? ''}
                      onChange={(event) => updateCredential(field.key, event.currentTarget.value)}
                      error={fieldErrors[field.key]}
                    />
                  ))}
                </Stack>
              ) : entry ? (
                <Alert
                  color="blue"
                  variant="light"
                  title="No API credentials needed"
                >
                  {entry.displayName} sends orders to your webhook address. You will still use the
                  signing secret below to prove each request is genuine.
                </Alert>
              ) : null}

              <Box>
                <Title order={2} size="h5" mb="xs">
                  Signing secret
                </Title>
                <TextInput
                  label="Signing secret"
                  description="At least 16 characters. StockLedger uses it to prove each incoming order really came from this channel."
                  withAsterisk
                  aria-required="true"
                  type={secretVisible ? 'text' : 'password'}
                  value={values.webhookSecret}
                  onChange={(event) => {
                    const { value } = event.currentTarget;
                    setValues((current) => ({ ...current, webhookSecret: value }));
                  }}
                  error={fieldErrors.webhookSecret}
                  rightSectionPointerEvents="all"
                  rightSection={
                    <ActionIcon
                      variant="subtle"
                      color="gray"
                      aria-label={
                        secretVisible ? 'Hide signing secret' : 'Show signing secret'
                      }
                      onClick={() => setSecretVisible((visible) => !visible)}
                    >
                      {secretVisible ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                    </ActionIcon>
                  }
                />
                <Group gap="xs" mt="xs">
                  <Button
                    type="button"
                    variant="subtle"
                    size="compact-xs"
                    leftSection={<IconKey size={13} />}
                    onClick={generateSecret}
                  >
                    Generate secret
                  </Button>
                </Group>
              </Box>

              <Box>
                <Button
                  type="button"
                  variant="subtle"
                  size="compact-xs"
                  leftSection={<IconAdjustments size={14} />}
                  rightSection={
                    <IconChevronDown
                      size={14}
                      style={{ transform: advancedOpen ? 'rotate(180deg)' : undefined }}
                    />
                  }
                  aria-expanded={advancedOpen}
                  onClick={() => setAdvancedOpen((open) => !open)}
                >
                  Advanced
                </Button>
                <Collapse in={advancedOpen}>
                  <Stack gap="sm" mt="sm">
                    <TextInput
                      label="Custom webhook address"
                      description="Leave blank to derive it from the connection name. Letters, numbers, and hyphens only."
                      placeholder={
                        entry ? slugifyWebhookAddress(values.displayName) || defaultSlugOf(entry) : ''
                      }
                      value={customAddress}
                      onChange={(event) => setCustomAddress(event.currentTarget.value)}
                      error={fieldErrors.slug}
                    />
                    {entry && previewPath ? (
                      <Box>
                        <Text size="xs" c="dimmed">
                          Webhook URL
                        </Text>
                        <CopyableCode value={webhookUrlFor(previewPath)} label="Webhook URL" />
                      </Box>
                    ) : null}
                  </Stack>
                </Collapse>
              </Box>

              {error ? (
                <ProblemAlert error={error} title="Could not connect the platform" compact />
              ) : null}

              <Group justify="flex-end">
                <Button type="button" variant="default" component={Link} to="/settings/platforms">
                  Cancel
                </Button>
                <Button
                  type="submit"
                  leftSection={<IconPlugConnected size={16} />}
                  loading={createMutation.isPending}
                >
                  Connect platform
                </Button>
              </Group>
            </Stack>
          </form>
        </Card>
      )}
    </>
  );
}
