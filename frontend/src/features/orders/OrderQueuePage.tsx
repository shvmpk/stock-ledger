import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Badge, Box, Button, Group, SegmentedControl, Stack, Text } from '@mantine/core';
import { IconRefresh } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listOrders } from '../../lib/api/endpoints/orders';
import type { Order, OrderStatus } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { DataTable, MobileRow, type DataTableColumn } from '../../components/data/DataTable';
import { OrderStatusBadge } from '../../components/data/StatusBadge';
import { CopyableCode } from '../../components/data/CopyableCode';
import { Money } from '../../components/data/Money';
import { TablePagination } from '../../components/data/TablePagination';
import { EmptyState } from '../../components/feedback/EmptyState';
import { ErrorState } from '../../components/feedback/ErrorState';
import { formatDateTime } from '../../lib/format/date';
import { formatQuantity } from '../../lib/format/quantity';
import { OrderDetailDrawer } from './OrderDetailDrawer';
import { useParams } from 'react-router-dom';
import type { Product } from '../../lib/api/types';
import { listProducts } from '../../lib/api/endpoints/products';

const QUEUE_CONFIG = {
  new: {
    status: 'PENDING' as OrderStatus,
    label: 'New orders',
    description: 'Paid orders whose stock is reserved and waiting to be packed.',
  },
  waiting: {
    status: 'WAITING_FOR_STOCK' as OrderStatus,
    label: 'Waiting for stock',
    description: 'Async orders queued until a supplier receipt can allocate stock FIFO.',
  },
  ready: {
    status: 'READY_TO_SHIP' as OrderStatus,
    label: 'Ready to ship',
    description: 'Packed orders ready for a carrier and tracking number.',
  },
  shipped: {
    status: 'SHIPPED' as OrderStatus,
    label: 'Shipped',
    description: 'Dispatched orders with carrier cost and tracking.',
  },
} as const;

type QueueKey = keyof typeof QUEUE_CONFIG | 'returns';
const RETURN_STATUSES: OrderStatus[] = ['RETURNED', 'PARTIALLY_RETURNED', 'CANCELLED'];

function isReturnStatus(value: string | null): value is OrderStatus {
  return !!value && RETURN_STATUSES.includes(value as OrderStatus);
}

export function OrderQueuePage() {
  const { queue } = useParams();
  const queueKey: QueueKey = queue === 'returns' ? 'returns' : (queue as keyof typeof QUEUE_CONFIG);
  const [searchParams, setSearchParams] = useSearchParams();
  const [pageState, setPageState] = useState<{ scope: string; page: number }>({
    scope: '',
    page: 0,
  });
  const productsQuery = useQuery({ queryKey: queryKeys.products, queryFn: () => listProducts() });
  const products: Product[] = productsQuery.data ?? [];
  const orderId = searchParams.get('order');

  const returnStatus = isReturnStatus(searchParams.get('status'))
    ? (searchParams.get('status') as OrderStatus)
    : 'RETURNED';

  const status = queueKey === 'returns' ? returnStatus : QUEUE_CONFIG[queueKey]?.status;
  const scope = `${queueKey}:${returnStatus}`;
  const page = pageState.scope === scope ? pageState.page : 0;
  const setPage = (next: number) => setPageState({ scope, page: next });

  const query = useQuery({
    queryKey: queryKeys.orders({ status, page }),
    queryFn: () => listOrders({ status, page, size: 20 }),
    placeholderData: keepPreviousData,
    enabled: !!status,
  });

  const setOrderId = (id: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (id) next.set('order', id);
    else next.delete('order');
    setSearchParams(next, { replace: true });
  };

  const setReturnStatus = (nextStatus: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('status', nextStatus);
    next.delete('order');
    setSearchParams(next, { replace: true });
  };

  if (!QUEUE_CONFIG[queueKey as keyof typeof QUEUE_CONFIG] && queueKey !== 'returns') {
    return (
      <EmptyState
        title="Unknown order queue"
        description="Choose New, Waiting for stock, Ready to ship, Shipped, or Returns & cancellations."
      />
    );
  }

  const config =
    queueKey === 'returns'
      ? {
          label: 'Returns & cancellations',
          description: 'Returned, partially returned, and cancelled orders with their complete history.',
        }
      : QUEUE_CONFIG[queueKey];

  const rows = query.data?.data ?? [];
  const pagination = query.data?.pagination;

  const columns: Array<DataTableColumn<Order>> = [
    {
      key: 'orderNumber',
      header: 'Order',
      rowHeader: true,
      render: (order) => <CopyableCode value={order.orderNumber} label="Order number" />,
    },
    {
      key: 'customer',
      header: 'Customer',
      render: (order) => (
        <Stack gap={0}>
          <Text size="sm" fw={500}>
            {order.customerName}
          </Text>
          {order.customerEmail ? (
            <Text size="xs" c="dimmed">
              {order.customerEmail}
            </Text>
          ) : null}
        </Stack>
      ),
    },
    {
      key: 'items',
      header: 'Items',
      render: (order) => (
        <Text size="xs" lineClamp={2}>
          {order.items.map((item) => `${formatQuantity(item.quantity)}× ${item.sku}`).join(', ')}
        </Text>
      ),
    },
    {
      key: 'source',
      header: 'Source',
      render: (order) => (
        <Badge variant="light" color="slate" size="sm" styles={{ label: { textTransform: 'none' } }}>
          {sourceLabel(order.source)}
        </Badge>
      ),
    },
    {
      key: 'shipping',
      header: 'Shipping charged',
      align: 'right',
      render: (order) => <Money value={order.customerShippingCharge} />,
    },
    {
      key: 'created',
      header: 'Created',
      render: (order) => (
        <Text size="xs" c="dimmed">
          {formatDateTime(order.createdAt)}
        </Text>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (order) => <OrderStatusBadge status={order.status} />,
    },
    {
      key: 'action',
      header: 'Actions',
      align: 'right',
      render: (order) => (
        <Button size="compact-xs" variant="subtle" onClick={() => setOrderId(order.id)}>
          Open
        </Button>
      ),
    },
  ];

  return (
    <>
      <Group justify="space-between" align="flex-end" mb="sm" wrap="wrap">
        <Box>
          <Text fw={650}>{config.label}</Text>
          <Text size="xs" c="dimmed">
            {config.description}
          </Text>
        </Box>
        <Group gap="sm">
          <Text size="xs" c="dimmed">
            Updated {query.dataUpdatedAt ? formatDateTime(new Date(query.dataUpdatedAt).toISOString()) : '—'}
          </Text>
          <Button
            size="compact-sm"
            variant="default"
            leftSection={<IconRefresh size={14} />}
            onClick={() => void query.refetch()}
            loading={query.isFetching}
          >
            Refresh
          </Button>
        </Group>
      </Group>

      {queueKey === 'returns' ? (
        <SegmentedControl
          mb="md"
          size="xs"
          value={returnStatus}
          onChange={setReturnStatus}
          data={[
            { value: 'RETURNED', label: 'Returned' },
            { value: 'PARTIALLY_RETURNED', label: 'Partially returned' },
            { value: 'CANCELLED', label: 'Cancelled' },
          ]}
        />
      ) : null}

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <DataTable
          data={rows}
          columns={columns}
          getRowKey={(order) => order.id}
          caption={`${config.label} — newest first`}
          loading={query.isLoading}
          onRowClick={(order) => setOrderId(order.id)}
          rowAriaLabel={(order) => `Open order ${order.orderNumber}`}
          minWidth={980}
          empty={
            queueKey === 'waiting' ? (
              <EmptyState
                title="No orders waiting for stock"
                description="Manual orders are rejected when stock is unavailable. CSV, webhook, and platform-sync orders can queue here and are allocated oldest-first when stock arrives."
                action={
                  <Button variant="light" onClick={() => void query.refetch()}>
                    Check again
                  </Button>
                }
              />
            ) : (
              <EmptyState
                title={`No ${config.label.toLowerCase()}`}
                description={
                  queueKey === 'returns'
                    ? 'Returned, partially returned, and cancelled orders will appear here.'
                    : 'Nothing in this queue right now.'
                }
                action={
                  <Button variant="light" onClick={() => void query.refetch()}>
                    Check again
                  </Button>
                }
              />
            )
          }
          mobileCard={(order) => (
            <Stack gap="xs">
              <Group justify="space-between" align="flex-start">
                <CopyableCode value={order.orderNumber} label="Order number" />
                <OrderStatusBadge status={order.status} />
              </Group>
              <Text size="sm" fw={600}>
                {order.customerName}
              </Text>
              <MobileRow label="Items">
                <Text size="xs" lineClamp={2}>
                  {order.items.map((item) => `${formatQuantity(item.quantity)}× ${item.sku}`).join(', ')}
                </Text>
              </MobileRow>
              <MobileRow label="Source">{sourceLabel(order.source)}</MobileRow>
              <MobileRow label="Shipping">
                <Money value={order.customerShippingCharge} />
              </MobileRow>
              <MobileRow label="Created">
                <Text size="xs">{formatDateTime(order.createdAt)}</Text>
              </MobileRow>
              <Button size="xs" variant="light" onClick={() => setOrderId(order.id)}>
                Open order
              </Button>
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
          onChange={setPage}
        />
      ) : null}

      <OrderDetailDrawer
        orderId={orderId}
        products={products}
        onClose={() => setOrderId(null)}
      />
    </>
  );
}

function sourceLabel(source: Order['source']): string {
  return {
    MANUAL: 'Manual',
    CSV: 'CSV import',
    WEBHOOK: 'Webhook',
    PLATFORM_SYNC: 'Platform sync',
  }[source];
}
