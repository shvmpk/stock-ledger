import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge,
  Box,
  Button,
  Group,
  SegmentedControl,
  Stack,
  Text,
} from '@mantine/core';
import {
  IconArrowLeft,
  IconChartBar,
  IconHelpCircle,
  IconPackageImport,
  IconSettings,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getReorderList } from '../../lib/api/endpoints/reorder';
import type { ReorderItem } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { useAuth } from '../../auth/useAuth';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { DataTable, MobileRow, type DataTableColumn } from '../../components/data/DataTable';
import { CopyableCode } from '../../components/data/CopyableCode';
import { VariantAvatar } from '../../components/domain/VariantAvatar';
import { formatQuantity } from '../../lib/format/quantity';
import { formatDateTime } from '../../lib/format/date';
import { ReceiveStockDrawer } from './ReceiveStockDrawer';
import { ReorderTraceDrawer } from './ReorderTraceDrawer';
import { LeadTimeModal } from './LeadTimeModal';
import { StockExplanationDrawer } from './StockExplanationDrawer';

type Filter = 'ALL' | 'OUT' | 'BELOW';

function ReorderStatusBadges({ item }: { item: ReorderItem }) {
  const status =
    item.available <= 0
      ? { color: 'red', label: 'Out of stock' }
      : item.reorderLevel !== null && item.available <= item.reorderLevel
        ? { color: 'amber', label: 'Below level' }
        : (item.suggestedQuantity ?? 0) > 0
          ? { color: 'blue', label: 'Cover short' }
          : { color: 'gray', label: 'Review' };

  return (
    <Stack gap={4} align="flex-start">
      <Badge
        color={status.color}
        variant="light"
        styles={{ label: { textTransform: 'none', fontWeight: 600 } }}
      >
        {status.label}
      </Badge>
      {item.historyStatus === 'INSUFFICIENT' ? (
        <Badge
          color="gray"
          variant="light"
          styles={{ label: { textTransform: 'none', fontWeight: 600 } }}
        >
          Not enough history
        </Badge>
      ) : null}
    </Stack>
  );
}

export function ReorderPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const owner = user?.role === 'OWNER';
  const query = useQuery({ queryKey: queryKeys.reorder, queryFn: getReorderList });
  const [filter, setFilter] = useState<Filter>('ALL');
  const [trace, setTrace] = useState<ReorderItem | null>(null);
  const [receive, setReceive] = useState<ReorderItem | null>(null);
  const [receiveOpen, setReceiveOpen] = useState(false);
  const [explainVariantId, setExplainVariantId] = useState<string | null>(null);
  const [leadTimeOpen, setLeadTimeOpen] = useState(false);

  const items = useMemo(() => query.data?.items ?? [], [query.data?.items]);
  const salesWindowDays = query.data?.salesWindowDays ?? 90;
  const leadTimeDays = query.data?.leadTimeDays ?? 14;
  const targetCoverDays = query.data?.targetCoverDays ?? 14;
  const minimumHistoryDays = query.data?.minimumHistoryDays ?? 21;
  const minimumUnitsSold = query.data?.minimumUnitsSold ?? 5;

  const outOfStock = items.filter((item) => item.available <= 0).length;
  const belowLevel = items.filter(
    (item) =>
      item.available > 0 &&
      item.reorderLevel !== null &&
      item.available <= item.reorderLevel,
  ).length;
  const suggestedUnits = items.reduce(
    (sum, item) => sum + (item.suggestedQuantity ?? 0),
    0,
  );
  const insufficientHistory = items.filter(
    (item) => item.historyStatus === 'INSUFFICIENT',
  ).length;

  const rows = useMemo(() => {
    return items.filter((item) => {
      if (filter === 'OUT') return item.available <= 0;
      if (filter === 'BELOW') {
        return (
          item.available > 0 &&
          item.reorderLevel !== null &&
          item.available <= item.reorderLevel
        );
      }
      return true;
    });
  }, [items, filter]);

  const openReceive = (item: ReorderItem | null = null) => {
    setReceive(item);
    setReceiveOpen(true);
  };

  const columns: Array<DataTableColumn<ReorderItem>> = [
    {
      key: 'product',
      header: 'Product',
      rowHeader: true,
      render: (row) => (
        <Group gap="sm" wrap="nowrap" align="flex-start">
          <VariantAvatar sku={row.sku} optionValue={row.optionValue} size="sm" />
          <Stack gap={2}>
            <Text size="sm" fw={600}>
              {row.productName}
            </Text>
            <Text size="xs" c="dimmed">
              {row.optionName}: {row.optionValue}
            </Text>
          </Stack>
        </Group>
      ),
    },
    {
      key: 'sku',
      header: 'SKU',
      render: (row) => <CopyableCode value={row.sku} label="SKU" />,
    },
    {
      key: 'available',
      header: 'Available',
      align: 'right',
      render: (row) => (
        <Stack gap={2} align="flex-end">
          <Text size="sm" fw={700} className="sl-tabular">
            {formatQuantity(row.available)}
          </Text>
          <Text size="xs" c="dimmed" className="sl-tabular">
            {formatQuantity(row.onHand)} on hand · {formatQuantity(row.reserved)} reserved
          </Text>
        </Stack>
      ),
    },
    {
      key: 'demand',
      header: `Net sold (${formatQuantity(salesWindowDays)}d)`,
      align: 'right',
      render: (row) => (
        <Stack gap={2} align="flex-end">
          <Text size="sm" fw={row.unitsSold > 0 ? 600 : 400} className="sl-tabular">
            {formatQuantity(row.unitsSold)} units
          </Text>
          <Text size="xs" c="dimmed">
            {row.ordersCount > 0
              ? `${formatQuantity(row.ordersCount)} shipped orders`
              : 'No shipped sales'}
          </Text>
        </Stack>
      ),
    },
    {
      key: 'cover',
      header: 'Cover',
      align: 'right',
      render: (row) =>
        row.daysOfCover === null ? (
          <Text size="sm" c="dimmed">
            —
          </Text>
        ) : (
          <Text size="sm" className="sl-tabular">
            {formatQuantity(row.daysOfCover)} days
          </Text>
        ),
    },
    {
      key: 'suggested',
      header: 'Suggested',
      align: 'right',
      render: (row) =>
        row.suggestedQuantity === null ? (
          <Text size="sm" c="dimmed">
            —
          </Text>
        ) : (
          <Text size="sm" fw={700} className="sl-tabular">
            {formatQuantity(row.suggestedQuantity)}
          </Text>
        ),
    },
    {
      key: 'status',
      header: 'Why',
      render: (row) => <ReorderStatusBadges item={row} />,
    },
    {
      key: 'actions',
      header: 'Actions',
      align: 'right',
      render: (row) => (
        <Group gap={4} justify="flex-end" wrap="nowrap">
          <Button
            size="compact-xs"
            variant="subtle"
            leftSection={<IconChartBar size={13} />}
            onClick={() => setTrace(row)}
          >
            Trace
          </Button>
          <Button
            size="compact-xs"
            variant="light"
            leftSection={<IconPackageImport size={13} />}
            onClick={() => openReceive(row)}
          >
            Receive
          </Button>
          <Button
            size="compact-xs"
            variant="subtle"
            leftSection={<IconHelpCircle size={13} />}
            onClick={() => setExplainVariantId(row.variantId)}
          >
            Why this number?
          </Button>
        </Group>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Reorder"
        description={`Buying guidance from net units shipped in the last ${formatQuantity(
          salesWindowDays,
        )} days. Suggestions cover a ${formatQuantity(
          leadTimeDays,
        )}-day supplier lead time plus ${formatQuantity(
          targetCoverDays,
        )} days of target cover, rounded up.`}
        actions={
          <>
            <Button
              variant="default"
              leftSection={<IconArrowLeft size={16} />}
              onClick={() => navigate('/stock/products')}
            >
              All products
            </Button>
            {owner ? (
              <Button
                variant="default"
                leftSection={<IconSettings size={16} />}
                onClick={() => setLeadTimeOpen(true)}
              >
                Edit lead time
              </Button>
            ) : null}
            <Button
              leftSection={<IconPackageImport size={16} />}
              onClick={() => openReceive()}
            >
              Receive stock
            </Button>
          </>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="Reorder summary">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(items.length)}</Text>
          <Text className="sl-summary-label">Variants to reorder</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(outOfStock)}</Text>
          <Text className="sl-summary-label">Out of stock</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(suggestedUnits)}</Text>
          <Text className="sl-summary-label">Suggested units</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(leadTimeDays)} days</Text>
          <Text className="sl-summary-label">Supplier lead time</Text>
        </Box>
      </Box>

      <Group justify="space-between" align="center" mb="sm" wrap="wrap">
        <SegmentedControl
          value={filter}
          onChange={(value) => setFilter(value as Filter)}
          data={[
            { value: 'ALL', label: `All (${items.length})` },
            { value: 'OUT', label: `Out of stock (${outOfStock})` },
            { value: 'BELOW', label: `Below level (${belowLevel})` },
          ]}
          size="xs"
        />
        <Text size="xs" c="dimmed">
          {insufficientHistory > 0
            ? `${formatQuantity(insufficientHistory)} ${
                insufficientHistory === 1 ? 'variant needs' : 'variants need'
              } at least ${formatQuantity(minimumHistoryDays)} days of shipped history and ${formatQuantity(
                minimumUnitsSold,
              )} net units before an estimate is shown.`
            : `Every variant has at least ${formatQuantity(
                minimumHistoryDays,
              )} days of shipped history and ${formatQuantity(
                minimumUnitsSold,
              )} net units behind its estimate.`}
          {query.data ? ` Generated ${formatDateTime(query.data.generatedAt)}.` : ''}
        </Text>
      </Group>

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <DataTable
          data={rows}
          columns={columns}
          getRowKey={(row) => row.variantId}
          caption="Reorder candidates, most urgent first"
          loading={query.isLoading}
          minWidth={1060}
          empty={
            <EmptyState
              title={
                filter === 'ALL'
                  ? 'Nothing needs reordering'
                  : 'No variants match this filter'
              }
              description={
                filter === 'ALL'
                  ? 'Every sellable variant is above its reorder level and has enough cover for the supplier lead time. Guidance updates as orders ship and returns are processed.'
                  : 'Try another filter to see the rest of the reorder list.'
              }
              action={
                <Button variant="light" onClick={() => navigate('/stock/products')}>
                  Back to all products
                </Button>
              }
            />
          }
          mobileCard={(row) => (
            <Stack gap="xs">
              <Group justify="space-between" align="flex-start" wrap="nowrap">
                <VariantAvatar sku={row.sku} optionValue={row.optionValue} size="sm" />
                <Box style={{ flex: 1, minWidth: 0 }}>
                  <Text size="sm" fw={600}>
                    {row.productName}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {row.optionName}: {row.optionValue}
                  </Text>
                </Box>
                <ReorderStatusBadges item={row} />
              </Group>
              <CopyableCode value={row.sku} label="SKU" />
              <MobileRow label="Available">
                <Text size="sm" fw={700} className="sl-tabular">
                  {formatQuantity(row.available)}
                </Text>
              </MobileRow>
              <MobileRow label={`Net sold (${salesWindowDays}d)`}>
                <Text size="sm" className="sl-tabular">
                  {formatQuantity(row.unitsSold)} units
                </Text>
              </MobileRow>
              <MobileRow label="Cover">
                <Text size="sm" className="sl-tabular">
                  {row.daysOfCover === null ? '—' : `${formatQuantity(row.daysOfCover)} days`}
                </Text>
              </MobileRow>
              <MobileRow label="Suggested">
                <Text size="sm" fw={700} className="sl-tabular">
                  {row.suggestedQuantity === null ? '—' : formatQuantity(row.suggestedQuantity)}
                </Text>
              </MobileRow>
              <Group gap="xs" grow>
                <Button
                  size="xs"
                  variant="light"
                  leftSection={<IconChartBar size={14} />}
                  onClick={() => setTrace(row)}
                >
                  Trace sales
                </Button>
                <Button
                  size="xs"
                  variant="light"
                  leftSection={<IconPackageImport size={14} />}
                  onClick={() => openReceive(row)}
                >
                  Receive stock
                </Button>
                <Button
                  size="xs"
                  variant="subtle"
                  leftSection={<IconHelpCircle size={14} />}
                  onClick={() => setExplainVariantId(row.variantId)}
                >
                  Why this number?
                </Button>
              </Group>
            </Stack>
          )}
        />
      )}

      <Text size="xs" c="dimmed" mt="md">
        Suggested quantities never go below zero. A variant with no reorder level and a fully
        covered estimate stays off this list; an unknown reorder level is never treated as zero.
      </Text>

      <ReorderTraceDrawer item={trace} onClose={() => setTrace(null)} />

      {explainVariantId ? (
        <StockExplanationDrawer
          variantId={explainVariantId}
          onClose={() => setExplainVariantId(null)}
        />
      ) : null}

      {receiveOpen ? (
        <ReceiveStockDrawer
          key={receive?.variantId ?? 'catalog'}
          opened
          onClose={() => {
            setReceiveOpen(false);
            setReceive(null);
          }}
          variantId={receive?.variantId}
          variantLabel={
            receive
              ? `${receive.productName} — ${receive.optionValue} · ${receive.sku}`
              : undefined
          }
        />
      ) : null}

      {owner && leadTimeOpen ? (
        <LeadTimeModal
          initialLeadTimeDays={leadTimeDays}
          targetCoverDays={targetCoverDays}
          onClose={() => setLeadTimeOpen(false)}
        />
      ) : null}
    </>
  );
}
