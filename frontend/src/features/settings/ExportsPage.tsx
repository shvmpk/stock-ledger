import { useState } from 'react';
import {
  Badge,
  Box,
  Button,
  Card,
  Group,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconBox,
  IconCheck,
  IconDownload,
  IconHistory,
  IconShoppingCart,
} from '@tabler/icons-react';
import { exportCsv, type ExportKind } from '../../lib/api/endpoints/exports';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';

const EXPORTS: Array<{
  kind: ExportKind;
  title: string;
  description: string;
  columns: string;
  icon: typeof IconBox;
}> = [
  {
    kind: 'catalog',
    title: 'Catalog',
    description: 'Every product variant with wholesale cost, retail price, weight, and the live ledger.',
    columns: 'Product, SKU, barcode, option, cost, retail, weight, on hand, reserved, available',
    icon: IconBox,
  },
  {
    kind: 'orders',
    title: 'Orders',
    description: 'Order headers with source, status, customer, shipping charge, actual cost, and tracking.',
    columns: 'Order number, source, status, customer, address, carrier, tracking, costs, timestamps',
    icon: IconShoppingCart,
  },
  {
    kind: 'history',
    title: 'Stock history',
    description: 'The immutable stock-movement ledger for reconciliation and external accounting.',
    columns: 'Movement ID, variant ID, on-hand delta, reserved delta, reason, reference, notes, time',
    icon: IconHistory,
  },
];

export function ExportsPage() {
  const [loading, setLoading] = useState<ExportKind | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [lastExported, setLastExported] = useState<{ kind: ExportKind; filename: string } | null>(null);

  const download = async (kind: ExportKind) => {
    setLoading(kind);
    setError(null);
    try {
      const filename = await exportCsv(kind);
      setLastExported({ kind, filename });
      notifications.show({
        title: 'Export ready',
        message: `${filename} downloaded.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Exports"
        description="Download complete CSV extracts of your product catalog, orders, and stock ledger history."
      />

      <Box mb="md">
        <Badge color="amber" variant="light">
          Owner access · contains financial data
        </Badge>
      </Box>

      {error ? <ProblemAlert error={error} title="Export failed" /> : null}

      {lastExported ? (
        <Text size="sm" c="dimmed" mb="md">
          Last download: <Text span fw={600}>{lastExported.filename}</Text>
        </Text>
      ) : null}

      <SimpleGrid cols={{ base: 1, md: 3 }} spacing="md">
        {EXPORTS.map((entry) => {
          const Icon = entry.icon;
          const isDownloading = loading === entry.kind;
          return (
            <Card key={entry.kind} withBorder padding="lg" radius="md">
              <Stack gap="sm" h="100%">
                <ThemeIcon variant="light" color="emerald" size={38} radius="sm">
                  <Icon size={20} />
                </ThemeIcon>
                <Title order={2} size="h4">
                  {entry.title}
                </Title>
                <Text size="sm" c="dimmed">
                  {entry.description}
                </Text>
                <Text size="xs" c="dimmed">
                  <Text span fw={600}>
                    Includes:
                  </Text>{' '}
                  {entry.columns}
                </Text>
                <Box mt="auto">
                  <Group justify="space-between" align="center" wrap="wrap" gap="xs">
                    <Badge variant="light" color="slate" size="sm">
                      CSV · UTF-8
                    </Badge>
                    <Button
                      size="xs"
                      leftSection={<IconDownload size={14} />}
                      loading={isDownloading}
                      onClick={() => void download(entry.kind)}
                    >
                      Download {entry.title.toLowerCase()}
                    </Button>
                  </Group>
                </Box>
              </Stack>
            </Card>
          );
        })}
      </SimpleGrid>

      <Text size="xs" c="dimmed" mt="lg">
        Exports are generated from committed data and download through the authenticated session. Staff
        accounts cannot access this page or the export endpoints.
      </Text>
    </>
  );
}
