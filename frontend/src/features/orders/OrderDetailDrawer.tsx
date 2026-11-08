import { useState } from 'react';
import {
  Anchor,
  Badge,
  Box,
  Button,
  Divider,
  Drawer,
  Group,
  Loader,
  Modal,
  Paper,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconArrowBackUp,
  IconBan,
  IconCheck,
  IconExternalLink,
  IconPackage,
  IconTruck,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cancelOrder, getOrder, listReturns, packOrder } from '../../lib/api/endpoints/orders';
import type { Order, Product, ReturnRecord } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { OrderItemsTable } from '../../components/domain/OrderItemsTable';
import { OrderStatusBadge } from '../../components/data/StatusBadge';
import { Money } from '../../components/data/Money';
import { formatDateTime } from '../../lib/format/date';
import { formatMinorUnits, lineTotal, toMinorUnits } from '../../lib/format/money';
import { carrierLabels, parcelLabels } from '../../theme/tokens';
import { DispatchModal } from './DispatchModal';
import { ReturnModal } from './ReturnModal';
import { StockExplanationDrawer } from '../stock/StockExplanationDrawer';

function sourceLabel(source: Order['source']): string {
  return {
    MANUAL: 'Manual',
    CSV: 'CSV import',
    WEBHOOK: 'Webhook',
    PLATFORM_SYNC: 'Platform sync',
  }[source];
}

function conditionBadge(condition: 'SELLABLE' | 'DAMAGED') {
  return (
    <Badge color={condition === 'SELLABLE' ? 'green' : 'orange'} variant="light" size="sm">
      {condition === 'SELLABLE' ? 'Sellable' : 'Damaged'}
    </Badge>
  );
}

export function OrderDetailDrawer({
  orderId,
  products,
  onClose,
}: {
  orderId: string | null;
  products: Product[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [shipOpen, setShipOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Customer requested cancellation');
  const [explainVariantId, setExplainVariantId] = useState<string | null>(null);

  const orderQuery = useQuery({
    queryKey: queryKeys.order(orderId ?? ''),
    queryFn: () => getOrder(orderId as string),
    enabled: !!orderId,
  });

  const returnsQuery = useQuery({
    queryKey: queryKeys.orderReturns(orderId ?? ''),
    queryFn: () => listReturns(orderId as string),
    enabled: !!orderId,
  });

  const invalidateOrderWorld = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['orders'] }),
      queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
      queryClient.invalidateQueries({ queryKey: queryKeys.products }),
      queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
      queryClient.invalidateQueries({ queryKey: ['movements'] }),
    ]);
  };

  const packMutation = useMutation({
    mutationFn: () => packOrder(orderId as string),
    onSuccess: async (updated) => {
      queryClient.setQueryData(queryKeys.order(updated.id), updated);
      notifications.show({
        title: 'Order packed',
        message: `${updated.orderNumber} is ready to ship.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      await invalidateOrderWorld();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => cancelOrder(orderId as string, reason),
    onSuccess: async (updated) => {
      queryClient.setQueryData(queryKeys.order(updated.id), updated);
      setCancelOpen(false);
      notifications.show({
        title: 'Order cancelled',
        message: `${updated.orderNumber} was cancelled and any reservation was released.`,
        color: 'gray',
        icon: <IconBan size={18} />,
      });
      await invalidateOrderWorld();
    },
  });

  const order = orderQuery.data;
  const returns = returnsQuery.data ?? [];
  const itemsSubtotal = order
    ? order.items.reduce((sum, item) => sum + lineTotal(item.unitPrice, item.quantity), 0)
    : 0;
  const total = order ? itemsSubtotal + toMinorUnits(order.customerShippingCharge) : 0;

  if (!orderId) return null;

  const handleChanged = async (updated: Order) => {
    queryClient.setQueryData(queryKeys.order(updated.id), updated);
    await invalidateOrderWorld();
    await queryClient.invalidateQueries({ queryKey: queryKeys.orderReturns(updated.id) });
  };

  return (
    <>
      <Drawer
        opened={!!orderId}
        onClose={onClose}
        position="right"
        size="xl"
        title={
          <Group gap="sm">
            <Text className="sl-code" fw={700}>
              {order?.orderNumber ?? 'Order'}
            </Text>
            {order ? <OrderStatusBadge status={order.status} /> : null}
          </Group>
        }
        overlayProps={{ backgroundOpacity: 0.35, blur: 2 }}
      >
        {orderQuery.isLoading ? (
          <Group>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading order…
            </Text>
          </Group>
        ) : orderQuery.isError ? (
          <ProblemAlert error={orderQuery.error} title="Order unavailable" />
        ) : !order ? null : (
          <Stack gap="lg">
            {packMutation.isError ? (
              <ProblemAlert error={packMutation.error} title="Could not pack order" />
            ) : null}
            {cancelMutation.isError ? (
              <ProblemAlert error={cancelMutation.error} title="Could not cancel order" />
            ) : null}

            <Group gap="lg" wrap="wrap">
              <Box>
                <Text size="xs" c="dimmed">
                  Source
                </Text>
                <Text size="sm" fw={600}>
                  {sourceLabel(order.source)}
                </Text>
              </Box>
              <Box>
                <Text size="xs" c="dimmed">
                  Created
                </Text>
                <Text size="sm" fw={600}>
                  {formatDateTime(order.createdAt)}
                </Text>
              </Box>
              {order.shippedAt ? (
                <Box>
                  <Text size="xs" c="dimmed">
                    Shipped
                  </Text>
                  <Text size="sm" fw={600}>
                    {formatDateTime(order.shippedAt)}
                  </Text>
                </Box>
              ) : null}
              {order.returnedAt ? (
                <Box>
                  <Text size="xs" c="dimmed">
                    Returned
                  </Text>
                  <Text size="sm" fw={600}>
                    {formatDateTime(order.returnedAt)}
                  </Text>
                </Box>
              ) : null}
            </Group>

            <Paper withBorder p="md" radius="md">
              <Stack gap="xs">
                <Title order={3} size="h5">
                  Customer
                </Title>
                <Text size="sm" fw={600}>
                  {order.customerName}
                </Text>
                {order.customerEmail ? (
                  <Anchor href={`mailto:${order.customerEmail}`} size="sm">
                    {order.customerEmail}
                  </Anchor>
                ) : (
                  <Text size="sm" c="dimmed">
                    No email provided
                  </Text>
                )}
                <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                  {order.shippingAddress}
                </Text>
              </Stack>
            </Paper>

            <OrderItemsTable
              items={order.items}
              products={products}
              onExplain={(variantId) => setExplainVariantId(variantId)}
            />

            <Stack gap={4}>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Items subtotal
                </Text>
                <Text size="sm" className="sl-tabular">
                  {formatMinorUnits(itemsSubtotal)}
                </Text>
              </Group>
              <Group justify="space-between">
                <Text size="sm" c="dimmed">
                  Shipping charged
                </Text>
                <Money value={order.customerShippingCharge} />
              </Group>
              <Divider my={4} />
              <Group justify="space-between">
                <Text fw={700}>Order total</Text>
                <Text fw={700} className="sl-tabular">
                  {formatMinorUnits(total)}
                </Text>
              </Group>
              {order.actualShippingCost !== null ? (
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    Actual carrier cost
                  </Text>
                  <Money value={order.actualShippingCost} size="xs" />
                </Group>
              ) : null}
            </Stack>

            {order.status === 'SHIPPED' ||
            order.status === 'RETURNED' ||
            order.status === 'PARTIALLY_RETURNED' ? (
              <Paper withBorder p="md" radius="md">
                <Stack gap="xs">
                  <Title order={3} size="h5">
                    Shipment
                  </Title>
                  <Group gap="lg" wrap="wrap">
                    <Box>
                      <Text size="xs" c="dimmed">
                        Carrier
                      </Text>
                      <Text size="sm" fw={600}>
                        {order.carrier ? carrierLabels[order.carrier] : '—'}
                      </Text>
                    </Box>
                    <Box>
                      <Text size="xs" c="dimmed">
                        Parcel
                      </Text>
                      <Text size="sm" fw={600}>
                        {order.parcelSize ? parcelLabels[order.parcelSize] : '—'}
                      </Text>
                    </Box>
                    <Box>
                      <Text size="xs" c="dimmed">
                        Tracking
                      </Text>
                      <Text size="sm" fw={600} className="sl-code">
                        {order.trackingNumber ?? '—'}
                      </Text>
                    </Box>
                    <Box>
                      <Text size="xs" c="dimmed">
                        Actual cost
                      </Text>
                      <Money value={order.actualShippingCost} />
                    </Box>
                  </Group>
                  {order.trackingUrl ? (
                    <Button
                      component="a"
                      href={order.trackingUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      variant="light"
                      size="xs"
                      leftSection={<IconTruck size={15} />}
                      rightSection={<IconExternalLink size={13} />}
                      w="fit-content"
                    >
                      Track parcel
                    </Button>
                  ) : null}
                </Stack>
              </Paper>
            ) : null}

            {order.status === 'SHIPPED' ||
            order.status === 'RETURNED' ||
            order.status === 'PARTIALLY_RETURNED' ||
            returns.length > 0 ? (
              <Box>
                <Group justify="space-between" mb="xs">
                  <Title order={3} size="h5">
                    Returns
                  </Title>
                  <Text size="xs" c="dimmed">
                    {returns.length} recorded
                  </Text>
                </Group>
                {returnsQuery.isLoading ? (
                  <Loader size="sm" />
                ) : returnsQuery.isError ? (
                  <ProblemAlert error={returnsQuery.error} title="Return history unavailable" />
                ) : returns.length === 0 ? (
                  <Text size="sm" c="dimmed">
                    No returns recorded for this order.
                  </Text>
                ) : (
                  <Stack gap="sm">
                    {returns.map((record) => (
                      <ReturnCard key={record.id} record={record} />
                    ))}
                  </Stack>
                )}
              </Box>
            ) : null}

            <Divider />
            <Group justify="flex-end" gap="xs" wrap="wrap">
              {order.status === 'PENDING' ? (
                <>
                  <Button
                    variant="default"
                    color="red"
                    leftSection={<IconBan size={16} />}
                    onClick={() => setCancelOpen(true)}
                  >
                    Cancel order
                  </Button>
                  <Button
                    variant="light"
                    leftSection={<IconTruck size={16} />}
                    onClick={() => setShipOpen(true)}
                  >
                    Ship
                  </Button>
                  <Button
                    leftSection={<IconPackage size={16} />}
                    loading={packMutation.isPending}
                    onClick={() => packMutation.mutate()}
                  >
                    Pack order
                  </Button>
                </>
              ) : null}

              {order.status === 'WAITING_FOR_STOCK' ? (
                <Button
                  variant="default"
                  color="red"
                  leftSection={<IconBan size={16} />}
                  onClick={() => setCancelOpen(true)}
                >
                  Cancel order
                </Button>
              ) : null}

              {order.status === 'READY_TO_SHIP' ? (
                <>
                  <Button
                    variant="default"
                    color="red"
                    leftSection={<IconBan size={16} />}
                    onClick={() => setCancelOpen(true)}
                  >
                    Cancel order
                  </Button>
                  <Button
                    leftSection={<IconTruck size={16} />}
                    onClick={() => setShipOpen(true)}
                  >
                    Ship
                  </Button>
                </>
              ) : null}

              {order.status === 'SHIPPED' || order.status === 'PARTIALLY_RETURNED' ? (
                <Button
                  variant="light"
                  leftSection={<IconArrowBackUp size={16} />}
                  onClick={() => setReturnOpen(true)}
                >
                  Process return
                </Button>
              ) : null}
            </Group>
          </Stack>
        )}
      </Drawer>

      {order && shipOpen ? (
        <DispatchModal
          key={`dispatch-${order.id}`}
          order={order}
          opened
          onClose={() => setShipOpen(false)}
          onShipped={(updated) => {
            setShipOpen(false);
            void handleChanged(updated);
          }}
        />
      ) : null}
      {order && returnOpen ? (
        <ReturnModal
          key={`return-${order.id}`}
          order={order}
          opened
          onClose={() => setReturnOpen(false)}
          onReturned={(updated) => {
            setReturnOpen(false);
            void handleChanged(updated);
          }}
        />
      ) : null}

      {explainVariantId ? (
        <StockExplanationDrawer
          variantId={explainVariantId}
          onClose={() => setExplainVariantId(null)}
        />
      ) : null}

      <Modal opened={cancelOpen} onClose={() => setCancelOpen(false)} title="Cancel order" size="sm">
        <Stack gap="md">
          <Text size="sm">
            Cancelling releases any reserved stock and writes an immutable ledger movement. This cannot
            be undone.
          </Text>
          <TextInput
            label="Reason"
            value={cancelReason}
            onChange={(event) => setCancelReason(event.currentTarget.value)}
            required
          />
          {cancelMutation.isError ? (
            <ProblemAlert error={cancelMutation.error} title="Could not cancel order" compact />
          ) : null}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setCancelOpen(false)}>
              Keep order
            </Button>
            <Button
              color="red"
              loading={cancelMutation.isPending}
              disabled={!cancelReason.trim()}
              onClick={() => cancelMutation.mutate(cancelReason.trim())}
            >
              Cancel order
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

function ReturnCard({ record }: { record: ReturnRecord }) {
  return (
    <Paper withBorder p="sm" radius="md">
      <Stack gap="xs">
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <Box>
            <Text size="sm" fw={600}>
              {record.returnReason || 'Return recorded'}
            </Text>
            <Text size="xs" c="dimmed">
              {formatDateTime(record.processedAt)}
              {record.processedBy ? ` · ${record.processedBy.fullName}` : ''}
            </Text>
          </Box>
          <Badge variant="light" color="gray">
            {record.items.reduce((sum, item) => sum + item.quantity, 0)} units
          </Badge>
        </Group>
        {record.operatorNotes ? (
          <Text size="xs" c="dimmed">
            Notes: {record.operatorNotes}
          </Text>
        ) : null}
        <Stack gap={4}>
          {record.items.map((item) => (
            <Group key={item.id} justify="space-between" gap="sm" wrap="nowrap">
              <Text size="xs" className="sl-code">
                {item.sku}
              </Text>
              <Group gap="xs">
                <Text size="xs" className="sl-tabular">
                  {item.quantity}×
                </Text>
                {conditionBadge(item.condition)}
                <Badge color={item.restocked ? 'green' : 'gray'} variant="light" size="sm">
                  {item.restocked ? 'Restocked' : 'Not restocked'}
                </Badge>
              </Group>
            </Group>
          ))}
        </Stack>
      </Stack>
    </Paper>
  );
}
