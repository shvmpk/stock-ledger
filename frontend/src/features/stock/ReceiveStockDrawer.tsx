import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Badge,
  Button,
  Drawer,
  Group,
  Loader,
  NumberInput,
  Stack,
  Text,
  Textarea,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCircleCheck, IconPackageImport } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listProducts } from '../../lib/api/endpoints/products';
import { receiveStock } from '../../lib/api/endpoints/inventory';
import type { ProductVariant, ReceiveStockResult } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { VariantSelect } from '../../components/domain/VariantSelect';
import { formatQuantity } from '../../lib/format/quantity';

interface SuccessState {
  result: ReceiveStockResult;
  quantity: number;
}

export function ReceiveStockDrawer({
  opened,
  onClose,
  variantId,
  variantSku,
  variantLabel,
}: {
  opened: boolean;
  onClose: () => void;
  variantId?: string;
  variantSku?: string;
  variantLabel?: string;
}) {
  const queryClient = useQueryClient();
  const productsQuery = useQuery({ queryKey: queryKeys.products, queryFn: () => listProducts() });
  const products = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);
  const [pickedSku, setPickedSku] = useState<string | null>(null);
  const [quantity, setQuantity] = useState<number | ''>(1);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<SuccessState | null>(null);

  const initialSku = useMemo(() => {
    if (variantId) {
      for (const product of products) {
        const match = product.variants.find((variant) => variant.id === variantId);
        if (match) return match.sku;
      }
    }
    return variantSku ?? null;
  }, [products, variantId, variantSku]);

  const selectedSku = pickedSku ?? initialSku;

  const selectedVariant: ProductVariant | null = useMemo(() => {
    if (!selectedSku) return null;
    for (const product of products) {
      const match = product.variants.find((variant) => variant.sku === selectedSku);
      if (match) return match;
    }
    return null;
  }, [products, selectedSku]);

  const mutation = useMutation({
    mutationFn: receiveStock,
    onSuccess: async (result, variables) => {
      setSuccess({ result, quantity: variables.quantity });
      setError(null);
      notifications.show({
        title: 'Stock received',
        message: `${formatQuantity(variables.quantity)} units added to ${result.stockLevel.sku}.`,
        color: 'emerald',
        icon: <IconCircleCheck size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.products }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: queryKeys.movements({}) }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
      ]);
    },
    onError: (caught) => setError(caught),
  });

  const submit = () => {
    setError(null);
    if (!selectedVariant) {
      setError(new Error('Choose a product variant before receiving stock.'));
      return;
    }
    if (typeof quantity !== 'number' || !Number.isInteger(quantity) || quantity <= 0) {
      setError(new Error('Quantity must be a whole number greater than zero.'));
      return;
    }
    mutation.mutate({
      productVariantId: selectedVariant.id,
      quantity,
      notes: notes.trim() || undefined,
    });
  };

  const beforeOnHand = success
    ? success.result.stockLevel.onHandQuantity - success.quantity
    : selectedVariant?.onHand;

  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      title="Receive stock"
      size="md"
      position="right"
      overlayProps={{ backgroundOpacity: 0.35, blur: 2 }}
    >
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          Record a supplier delivery. Receiving writes an immutable ledger movement and immediately
          allocates any waiting orders in oldest-first order.
        </Text>

        {productsQuery.isLoading ? (
          <Group>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading catalog…
            </Text>
          </Group>
        ) : productsQuery.isError ? (
          <ProblemAlert error={productsQuery.error} title="Catalog unavailable" />
        ) : success ? (
          <Stack gap="md">
            <Alert color="emerald" variant="light" icon={<IconCircleCheck size={18} />} title="Received">
              <Stack gap="xs">
                <Text size="sm">
                  {success.result.stockLevel.sku}: on hand {formatQuantity(beforeOnHand ?? 0)} →{' '}
                  <Text span fw={700} className="sl-tabular">
                    {formatQuantity(success.result.stockLevel.onHandQuantity)}
                  </Text>
                  ; available now {formatQuantity(success.result.stockLevel.availableQuantity)}.
                </Text>
                <Text size="xs" c="dimmed">
                  Reserved after allocation: {formatQuantity(success.result.stockLevel.reservedQuantity)}
                </Text>
              </Stack>
            </Alert>

            <Stack gap="xs">
              <Text fw={600} size="sm">
                Waiting-order allocation
              </Text>
              {success.result.allocatedOrderNumbers.length > 0 ? (
                <>
                  <Text size="sm">Allocated to waiting orders:</Text>
                  <Group gap="xs">
                    {success.result.allocatedOrderNumbers.map((orderNumber) => (
                      <Badge key={orderNumber} variant="light" color="emerald" className="sl-code">
                        {orderNumber}
                      </Badge>
                    ))}
                  </Group>
                  <Text size="xs" c="dimmed">
                    <Link to="/orders/new">Open the New queue</Link> to pack and dispatch them.
                  </Text>
                </>
              ) : (
                <Text size="sm" c="dimmed">
                  No waiting orders needed this stock. The units are available for new orders.
                </Text>
              )}
            </Stack>

            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                Close
              </Button>
              <Button
                onClick={() => {
                  setSuccess(null);
                  setQuantity(1);
                  setNotes('');
                }}
                leftSection={<IconPackageImport size={16} />}
              >
                Receive more
              </Button>
            </Group>
          </Stack>
        ) : (
          <Stack gap="sm">
            {initialSku && (variantId || variantSku) ? (
              <Stack gap={2}>
                <Text size="sm" fw={600}>
                  Variant
                </Text>
                <Text size="sm">{variantLabel ?? initialSku}</Text>
                <Text size="xs" c="dimmed" className="sl-code">
                  {initialSku}
                </Text>
              </Stack>
            ) : (
              <VariantSelect
                products={products}
                value={selectedSku}
                onSelect={(variant) => setPickedSku(variant?.sku ?? null)}
                label="Variant"
                required
              />
            )}

            {selectedVariant ? (
              <Text size="xs" c="dimmed">
                Current on hand: {formatQuantity(selectedVariant.onHand)} · reserved{' '}
                {formatQuantity(selectedVariant.reserved)} · available{' '}
                {formatQuantity(selectedVariant.available)}
              </Text>
            ) : null}

            <NumberInput
              label="Quantity received"
              description="Whole units added to physical on-hand stock."
              min={1}
              step={1}
              allowDecimal={false}
              value={quantity}
              onChange={(value) => setQuantity(typeof value === 'number' ? value : '')}
              required
            />
            <Textarea
              label="Notes"
              description="Optional supplier reference, delivery note, or batch detail."
              value={notes}
              onChange={(event) => setNotes(event.currentTarget.value)}
              minRows={2}
              maxLength={2000}
              autosize
            />

            {error ? <ProblemAlert error={error} title="Could not receive stock" /> : null}

            <Group justify="flex-end" mt="xs">
              <Button variant="default" onClick={onClose} disabled={mutation.isPending}>
                Cancel
              </Button>
              <Button
                onClick={submit}
                loading={mutation.isPending}
                leftSection={<IconPackageImport size={16} />}
              >
                Receive stock
              </Button>
            </Group>
          </Stack>
        )}
      </Stack>
    </Drawer>
  );
}
