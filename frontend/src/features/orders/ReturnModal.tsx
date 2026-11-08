import { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconArrowBackUp, IconCheck } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getOrder, listReturns, processReturn } from '../../lib/api/endpoints/orders';
import type { ItemCondition, Order, ReturnRecord } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { formatQuantity } from '../../lib/format/quantity';

interface ReturnRow {
  quantity: number | '';
  condition: ItemCondition;
}

export function ReturnModal({
  order,
  opened,
  onClose,
  onReturned,
}: {
  order: Order;
  opened: boolean;
  onClose: () => void;
  onReturned: (order: Order) => void;
}) {
  const queryClient = useQueryClient();
  const returnsQuery = useQuery({
    queryKey: queryKeys.orderReturns(order.id),
    queryFn: () => listReturns(order.id),
    enabled: opened,
  });
  const [rows, setRows] = useState<Record<string, ReturnRow>>(() => {
    const next: Record<string, ReturnRow> = {};
    for (const item of order.items) {
      next[item.id] = { quantity: 0, condition: 'SELLABLE' };
    }
    return next;
  });
  const [reason, setReason] = useState('Customer return');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<{ record: ReturnRecord; updatedOrder: Order } | null>(null);

  const returnedByItem = useMemo(() => {
    const map = new Map<string, number>();
    for (const record of returnsQuery.data ?? []) {
      for (const item of record.items) {
        map.set(item.orderItemId, (map.get(item.orderItemId) ?? 0) + item.quantity);
      }
    }
    return map;
  }, [returnsQuery.data]);

  const updateRow = (itemId: string, patch: Partial<ReturnRow>) => {
    setRows((current) => ({
      ...current,
      [itemId]: { ...(current[itemId] ?? { quantity: 0, condition: 'SELLABLE' }), ...patch },
    }));
  };

  const mutation = useMutation({
    mutationFn: async () => {
      const selectedItems = order.items
        .map((item) => ({
          orderItemId: item.id,
          quantity: typeof rows[item.id]?.quantity === 'number' ? (rows[item.id].quantity as number) : 0,
          condition: rows[item.id]?.condition ?? 'SELLABLE',
        }))
        .filter((item) => item.quantity > 0);

      const record = await processReturn(order.id, {
        reason: reason.trim() || undefined,
        operatorNotes: notes.trim() || undefined,
        items: selectedItems,
      });
      const updatedOrder = await getOrder(order.id);
      return { record, updatedOrder };
    },
    onSuccess: async ({ record, updatedOrder }) => {
      setSuccess({ record, updatedOrder });
      notifications.show({
        title: 'Return recorded',
        message: `${updatedOrder.orderNumber} now has ${updatedOrder.status.replace('_', ' ').toLowerCase()}.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
        queryClient.invalidateQueries({ queryKey: queryKeys.order(updatedOrder.id) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderReturns(updatedOrder.id) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.products }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
      ]);
    },
    onError: async (caught) => {
      setError(caught);
      await queryClient.invalidateQueries({ queryKey: queryKeys.orderReturns(order.id) });
    },
  });

  const submit = () => {
    setError(null);
    const selected = order.items
      .map((item) => ({
        item,
        quantity: typeof rows[item.id]?.quantity === 'number' ? (rows[item.id].quantity as number) : 0,
        remaining: item.quantity - (returnedByItem.get(item.id) ?? 0),
      }))
      .filter((entry) => entry.quantity > 0);

    if (selected.length === 0) {
      setError(new Error('Enter a return quantity for at least one item.'));
      return;
    }
    for (const entry of selected) {
      if (!Number.isInteger(entry.quantity) || entry.quantity <= 0) {
        setError(new Error(`Return quantity for ${entry.item.sku} must be a whole number greater than zero.`));
        return;
      }
      if (entry.quantity > entry.remaining) {
        setError(
          new Error(
            `Cannot return ${entry.quantity} units of ${entry.item.sku}; only ${entry.remaining} remain returnable.`,
          ),
        );
        return;
      }
    }
    if (notes.length > 2000) {
      setError(new Error('Operator notes must not exceed 2000 characters.'));
      return;
    }
    mutation.mutate();
  };

  const restocked = success?.record.items.filter((item) => item.restocked).reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  const writtenOff =
    success?.record.items.filter((item) => !item.restocked).reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Return items from ${order.orderNumber}`}
      size="lg"
      closeOnClickOutside={!mutation.isPending}
      closeOnEscape={!mutation.isPending}
    >
      {success ? (
        <Stack gap="md">
          <Alert color="emerald" variant="light" icon={<IconCheck size={18} />} title="Return processed">
            <Stack gap="xs">
              <Text size="sm">
                Restocked {formatQuantity(restocked)} sellable units; wrote off{' '}
                {formatQuantity(writtenOff)} damaged units.
              </Text>
              <Group gap="xs">
                {success.record.items.map((item) => (
                  <Badge key={item.id} variant="light" color={item.restocked ? 'green' : 'orange'}>
                    {item.sku} · {item.quantity}× {item.condition === 'SELLABLE' ? 'sellable' : 'damaged'}
                  </Badge>
                ))}
              </Group>
            </Stack>
          </Alert>
          <Group justify="flex-end">
            <Button
              onClick={() => {
                onReturned(success.updatedOrder);
              }}
            >
              Done
            </Button>
          </Group>
        </Stack>
      ) : (
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            Return quantities are capped by what has not already been returned. Sellable items restock
            on-hand stock; damaged items are written off without increasing sellable inventory.
          </Text>

          {returnsQuery.isLoading ? (
            <Group>
              <Loader size="sm" />
              <Text size="sm" c="dimmed">
                Checking previous returns…
              </Text>
            </Group>
          ) : returnsQuery.isError ? (
            <ProblemAlert error={returnsQuery.error} title="Return history unavailable" />
          ) : (
            <Table className="sl-data-table" captionSide="top">
              <Table.Caption className="sl-table-caption">Returnable items</Table.Caption>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th scope="col">Item</Table.Th>
                  <Table.Th scope="col" ta="right">
                    Ordered
                  </Table.Th>
                  <Table.Th scope="col" ta="right">
                    Returned
                  </Table.Th>
                  <Table.Th scope="col">Return qty</Table.Th>
                  <Table.Th scope="col">Condition</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {order.items.map((item) => {
                  const already = returnedByItem.get(item.id) ?? 0;
                  const remaining = item.quantity - already;
                  const row = rows[item.id] ?? { quantity: 0, condition: 'SELLABLE' };
                  return (
                    <Table.Tr key={item.id}>
                      <Table.Th scope="row" fw={500}>
                        <Text size="sm" className="sl-code">
                          {item.sku}
                        </Text>
                      </Table.Th>
                      <Table.Td ta="right" className="sl-tabular">
                        {formatQuantity(item.quantity)}
                      </Table.Td>
                      <Table.Td ta="right" className="sl-tabular">
                        {formatQuantity(already)}
                      </Table.Td>
                      <Table.Td>
                        {remaining <= 0 ? (
                          <Badge color="gray" variant="light">
                            Fully returned
                          </Badge>
                        ) : (
                          <NumberInput
                            aria-label={`Return quantity for ${item.sku}`}
                            value={row.quantity}
                            onChange={(value) =>
                              updateRow(item.id, { quantity: typeof value === 'number' ? value : '' })
                            }
                            min={0}
                            max={remaining}
                            allowDecimal={false}
                            size="xs"
                            w={100}
                          />
                        )}
                      </Table.Td>
                      <Table.Td>
                        {remaining <= 0 ? (
                          <Text size="xs" c="dimmed">
                            —
                          </Text>
                        ) : (
                          <Select
                            aria-label={`Condition for ${item.sku}`}
                            value={row.condition}
                            onChange={(value) =>
                              updateRow(item.id, { condition: (value as ItemCondition) ?? 'SELLABLE' })
                            }
                            data={[
                              { value: 'SELLABLE', label: 'Sellable — restock' },
                              { value: 'DAMAGED', label: 'Damaged — write off' },
                            ]}
                            allowDeselect={false}
                            size="xs"
                            w={180}
                          />
                        )}
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          )}

          <TextInput
            label="Reason"
            value={reason}
            onChange={(event) => setReason(event.currentTarget.value)}
            placeholder="Customer return — wrong size"
          />
          <Textarea
            label="Operator notes"
            description="Optional internal context. Maximum 2000 characters."
            value={notes}
            onChange={(event) => setNotes(event.currentTarget.value)}
            minRows={2}
            maxLength={2000}
            autosize
          />

          {error ? <ProblemAlert error={error} title="Cannot process return" /> : null}

          <Group justify="flex-end">
            <Button variant="default" onClick={onClose} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button
              leftSection={<IconArrowBackUp size={16} />}
              loading={mutation.isPending}
              onClick={submit}
            >
              Process return
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
  );
}
