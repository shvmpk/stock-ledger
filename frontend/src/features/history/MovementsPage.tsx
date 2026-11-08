import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Anchor, Alert, Badge, Box, Button, Group, Select, Stack, Text, TextInput } from '@mantine/core';
import { IconFilterOff, IconHistory, IconInfoCircle } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listMovements } from '../../lib/api/endpoints/inventory';
import type { StockMovement, StockMovementReason } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { DataTable, MobileRow, type DataTableColumn } from '../../components/data/DataTable';
import { FilterBar } from '../../components/data/FilterBar';
import { SearchInput } from '../../components/data/SearchInput';
import { TablePagination } from '../../components/data/TablePagination';
import { ReasonBadge } from '../../components/data/ReasonBadge';
import { Delta } from '../../components/data/Delta';
import { formatDateTime } from '../../lib/format/date';
import { endOfDayIso, startOfDayIso } from '../../lib/format/date';
import { movementReasonLabels } from '../../theme/tokens';
import { formatQuantity } from '../../lib/format/quantity';

const REASONS = Object.entries(movementReasonLabels) as Array<[StockMovementReason, string]>;

function isReason(value: string | null): value is StockMovementReason {
  return !!value && REASONS.some(([reason]) => reason === value);
}

export function MovementsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [dateError, setDateError] = useState<string | null>(null);

  const sku = searchParams.get('sku') ?? '';
  const reason = isReason(searchParams.get('reason')) ? (searchParams.get('reason') as StockMovementReason) : '';
  const from = searchParams.get('from') ?? '';
  const to = searchParams.get('to') ?? '';
  const parsedPage = Number(searchParams.get('page') ?? '0');
  const page = Number.isFinite(parsedPage) && parsedPage >= 0 ? Math.floor(parsedPage) : 0;

  const fromIso = from ? startOfDayIso(from) : undefined;
  const toIso = to ? endOfDayIso(to) : undefined;

  const query = useQuery({
    queryKey: queryKeys.movements({ sku, reason, from: fromIso, to: toIso, page, size: 20 }),
    queryFn: () =>
      listMovements({
        sku: sku.trim() || undefined,
        reason: reason || undefined,
        from: fromIso,
        to: toIso,
        page,
        size: 20,
      }),
    placeholderData: keepPreviousData,
  });

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setSearchParams(next, { replace: true });
  };

  const clearFilters = () => {
    setDateError(null);
    setSearchParams({}, { replace: true });
  };

  const setDate = (key: 'from' | 'to', value: string) => {
    setDateError(null);
    if (key === 'from' && to && value && value > to) {
      setDateError('The start date must be on or before the end date.');
      return;
    }
    if (key === 'to' && from && value && value < from) {
      setDateError('The end date must be on or after the start date.');
      return;
    }
    updateParam(key, value);
  };

  const rows = query.data?.data ?? [];
  const pagination = query.data?.pagination;
  const pageOnHand = rows.reduce((sum, row) => sum + row.deltaOnHand, 0);
  const pageReserved = rows.reduce((sum, row) => sum + row.deltaReserved, 0);
  const filtersActive = Boolean(sku || reason || from || to);

  const columns: Array<DataTableColumn<StockMovement>> = [
    {
      key: 'createdAt',
      header: 'When',
      rowHeader: true,
      render: (row) => (
        <Text size="xs" className="sl-tabular">
          {formatDateTime(row.createdAt)}
        </Text>
      ),
    },
    {
      key: 'product',
      header: 'Product / variant',
      render: (row) => (
        <Stack gap={0}>
          <Text size="sm" fw={500}>
            {row.productName}
          </Text>
          <Text size="xs" c="dimmed" className="sl-code">
            {row.sku}
          </Text>
        </Stack>
      ),
    },
    {
      key: 'reason',
      header: 'Reason',
      render: (row) => <ReasonBadge reason={row.reason} />,
    },
    {
      key: 'onHand',
      header: 'On-hand delta',
      align: 'right',
      render: (row) => <Delta value={row.deltaOnHand} label="On-hand delta" />,
    },
    {
      key: 'reserved',
      header: 'Reserved delta',
      align: 'right',
      render: (row) => <Delta value={row.deltaReserved} label="Reserved delta" />,
    },
    {
      key: 'reference',
      header: 'Reference',
      render: (row) => <ReferenceCell movement={row} />,
    },
    {
      key: 'actor',
      header: 'Actor',
      render: (row) => (
        <Text size="xs" c="dimmed">
          {row.actorName ?? 'System'}
        </Text>
      ),
    },
    {
      key: 'notes',
      header: 'Notes',
      render: (row) => (
        <Text size="xs" c="dimmed" lineClamp={2} maw={260}>
          {row.notes ?? '—'}
        </Text>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Stock movements"
        description="The append-only ledger behind every on-hand and reserved quantity. Filter by SKU, reason, or date."
        actions={
          <Button
            variant="default"
            leftSection={<IconFilterOff size={16} />}
            onClick={clearFilters}
            disabled={!filtersActive}
          >
            Clear filters
          </Button>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="Movement page summary">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(pagination?.totalElements ?? 0)}</Text>
          <Text className="sl-summary-label">Matching movements</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">
            <Delta value={pageOnHand} label="On-hand delta on this page" />
          </Text>
          <Text className="sl-summary-label">On-hand delta · page</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">
            <Delta value={pageReserved} label="Reserved delta on this page" />
          </Text>
          <Text className="sl-summary-label">Reserved delta · page</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(rows.length)}</Text>
          <Text className="sl-summary-label">Rows on this page</Text>
        </Box>
      </Box>

      {sku ? (
        <Alert
          color="blue"
          variant="light"
          icon={<IconInfoCircle size={18} />}
          aria-label="SKU movement summary"
          mb="md"
        >
          <Stack gap={2}>
            <Text size="sm" fw={600}>
              {formatQuantity(pagination?.totalElements ?? rows.length)}{' '}
              {(pagination?.totalElements ?? rows.length) === 1 ? 'movement' : 'movements'} for{' '}
              {sku} in this period
            </Text>
            <Text size="xs" c="dimmed" className="sl-tabular">
              {formatQuantity(pageOnHand)} on hand · {formatQuantity(pageReserved)} reserved on this
              page
            </Text>
          </Stack>
        </Alert>
      ) : null}

      <FilterBar>
        <Box style={{ flex: '1 1 240px', maxWidth: 380 }}>
          <SearchInput
            value={sku}
            onChange={(value) => updateParam('sku', value)}
            placeholder="Filter by SKU, e.g. HOOD-NVY-XL"
            label="Filter by SKU"
          />
        </Box>
        <Select
          label="Reason"
          value={reason || 'all'}
          onChange={(value) => updateParam('reason', value === 'all' ? '' : value ?? '')}
          data={[{ value: 'all', label: 'All reasons' }, ...REASONS.map(([value, label]) => ({ value, label }))]}
          w={210}
          allowDeselect={false}
        />
        <TextInput
          type="date"
          label="From"
          value={from}
          onChange={(event) => setDate('from', event.currentTarget.value)}
          w={160}
        />
        <TextInput
          type="date"
          label="To"
          value={to}
          onChange={(event) => setDate('to', event.currentTarget.value)}
          w={160}
        />
      </FilterBar>

      {dateError ? (
        <Text size="sm" c="red" mb="sm" role="alert">
          {dateError}
        </Text>
      ) : null}

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <DataTable
          data={rows}
          columns={columns}
          getRowKey={(row) => row.id}
          caption="Stock movements, newest first"
          loading={query.isLoading}
          minWidth={1120}
          empty={
            <EmptyState
              title="No movements match"
              description="Try a different SKU, reason, or date range. Every receipt, reservation, shipment, return, and audit writes here."
              action={
                filtersActive ? (
                  <Button variant="light" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : (
                  <Button variant="light" leftSection={<IconHistory size={16} />} onClick={() => void query.refetch()}>
                    Refresh
                  </Button>
                )
              }
            />
          }
          mobileCard={(row) => (
            <Stack gap="xs">
              <Group justify="space-between" align="flex-start">
                <Box>
                  <Text size="sm" fw={600}>
                    {row.productName}
                  </Text>
                  <Text size="xs" c="dimmed" className="sl-code">
                    {row.sku}
                  </Text>
                </Box>
                <ReasonBadge reason={row.reason} />
              </Group>
              <MobileRow label="When">
                <Text size="xs">{formatDateTime(row.createdAt)}</Text>
              </MobileRow>
              <MobileRow label="On hand">
                <Delta value={row.deltaOnHand} label="On-hand delta" />
              </MobileRow>
              <MobileRow label="Reserved">
                <Delta value={row.deltaReserved} label="Reserved delta" />
              </MobileRow>
              <MobileRow label="Reference">
                <ReferenceCell movement={row} />
              </MobileRow>
              <MobileRow label="Actor">{row.actorName ?? 'System'}</MobileRow>
              {row.notes ? (
                <Text size="xs" c="dimmed">
                  {row.notes}
                </Text>
              ) : null}
            </Stack>
          )}
        />
      )}

      {pagination ? (
        <TablePagination
          page={pagination.page}
          totalPages={pagination.totalPages}
          totalElements={pagination.totalElements}
          size={pagination.size}
          onChange={(next) => updateParam('page', String(next))}
        />
      ) : null}
    </>
  );
}

function ReferenceCell({ movement }: { movement: StockMovement }) {
  if (!movement.referenceType) {
    return (
      <Text size="xs" c="dimmed">
        —
      </Text>
    );
  }

  const shortId = movement.referenceId ? movement.referenceId.slice(0, 8) : null;
  const label = (
    <Badge variant="light" color="slate" size="sm" styles={{ label: { textTransform: 'none' } }}>
      {movement.referenceType.replaceAll('_', ' ').toLowerCase()}
    </Badge>
  );

  if (movement.referenceType === 'ORDER' && movement.referenceId) {
    return (
      <Stack gap={2}>
        {label}
        <Anchor component={Link} to={`/orders/new?order=${movement.referenceId}`} size="xs" className="sl-code">
          {shortId}
        </Anchor>
      </Stack>
    );
  }

  if (movement.referenceType === 'STOCK_COUNT' && movement.referenceId) {
    return (
      <Stack gap={2}>
        {label}
        <Anchor component={Link} to={`/stock/count?session=${movement.referenceId}`} size="xs" className="sl-code">
          {shortId}
        </Anchor>
      </Stack>
    );
  }

  return (
    <Stack gap={2}>
      {label}
      {shortId ? (
        <Text size="xs" c="dimmed" className="sl-code">
          {shortId}
        </Text>
      ) : null}
    </Stack>
  );
}
