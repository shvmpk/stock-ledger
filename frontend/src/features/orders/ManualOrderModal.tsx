import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
  Grid,
  Group,
  Loader,
  Modal,
  NumberInput,
  Stack,
  Text,
  TextInput,
  Textarea,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconCheck, IconPlus, IconTrash } from '@tabler/icons-react';
import { useForm } from '@mantine/form';
import { useMutation, useQuery } from '@tanstack/react-query';
import { listProducts } from '../../lib/api/endpoints/products';
import { createOrder } from '../../lib/api/endpoints/orders';
import type { Order, Product, ProductVariant } from '../../lib/api/types';
import { isApiError } from '../../lib/api/errors';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { VariantSelect } from '../../components/domain/VariantSelect';
import { formatMinorUnits, isValidMoney, lineTotal, parseMoneyInput, toMinorUnits } from '../../lib/format/money';
import { formatQuantity } from '../../lib/format/quantity';
import { useQueryClient } from '@tanstack/react-query';

interface ItemRow {
  key: string;
  sku: string;
  quantity: number | string;
  unitPrice: string;
}

function newItem(): ItemRow {
  return {
    key: globalThis.crypto?.randomUUID?.() ?? String(Date.now() + Math.random()),
    sku: '',
    quantity: 1,
    unitPrice: '',
  };
}

export function ManualOrderModal({
  opened,
  onClose,
  onCreated,
}: {
  opened: boolean;
  onClose: () => void;
  onCreated: (order: Order) => void;
}) {
  const queryClient = useQueryClient();
  const productsQuery = useQuery({ queryKey: queryKeys.products, queryFn: () => listProducts(), enabled: opened });
  const products: Product[] = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<unknown>(null);

  const form = useForm({
    initialValues: {
      customerName: '',
      customerEmail: '',
      shippingAddress: '',
      customerShippingCharge: '0.00',
      items: [newItem()],
    },
  });

  const variantBySku = useMemo(() => {
    const map = new Map<string, ProductVariant>();
    for (const product of products) {
      for (const variant of product.variants) map.set(variant.sku, variant);
    }
    return map;
  }, [products]);

  const requestedBySku = useMemo(() => {
    const map = new Map<string, number>();
    for (const item of form.values.items) {
      if (!item.sku) continue;
      const quantity = typeof item.quantity === 'number' ? item.quantity : 0;
      map.set(item.sku, (map.get(item.sku) ?? 0) + quantity);
    }
    return map;
  }, [form.values.items]);

  const subtotalMinor = form.values.items.reduce((sum, item) => {
    if (!item.sku || !isValidMoney(item.unitPrice)) return sum;
    const quantity = typeof item.quantity === 'number' ? item.quantity : 0;
    return sum + lineTotal(item.unitPrice, quantity);
  }, 0);
  const totalMinor = subtotalMinor + toMinorUnits(form.values.customerShippingCharge);

  const updateItem = (index: number, patch: Partial<ItemRow>) => {
    const next = form.values.items.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch } : item,
    );
    form.setFieldValue('items', next);
  };

  const validate = (): Record<string, string> => {
    const next: Record<string, string> = {};
    if (!form.values.customerName.trim()) next.customerName = 'Customer name is required.';
    if (!form.values.shippingAddress.trim()) next.shippingAddress = 'Shipping address is required.';
    if (!isValidMoney(form.values.customerShippingCharge)) {
      next.customerShippingCharge = 'Enter a valid amount with up to two decimals.';
    }
    if (form.values.customerEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.values.customerEmail.trim())) {
      next.customerEmail = 'Enter a valid email address.';
    }
    if (form.values.items.length === 0) {
      next.items = 'Add at least one item.';
    }

    form.values.items.forEach((item, index) => {
      if (!item.sku) {
        next[`items.${index}.sku`] = 'Choose a product variant.';
      }
      if (typeof item.quantity !== 'number' || !Number.isInteger(item.quantity) || item.quantity <= 0) {
        next[`items.${index}.quantity`] = 'Quantity must be a whole number greater than zero.';
      }
      if (!isValidMoney(item.unitPrice)) {
        next[`items.${index}.unitPrice`] = 'Enter a valid unit price.';
      }
      if (item.sku) {
        const variant = variantBySku.get(item.sku);
        const requested = requestedBySku.get(item.sku) ?? 0;
        if (variant && requested > variant.available) {
          next[`items.${index}.sku`] = `Only ${formatQuantity(variant.available)} available for ${item.sku}.`;
        }
      }
    });

    return next;
  };

  const mutation = useMutation({
    mutationFn: createOrder,
    onSuccess: async (order) => {
      notifications.show({
        title: 'Manual order created',
        message: `${order.orderNumber} reserved stock and is pending packing.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      form.reset();
      setErrors({});
      setApiError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
        queryClient.invalidateQueries({ queryKey: queryKeys.products }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
      ]);
      onCreated(order);
    },
    onError: (caught) => {
      setApiError(caught);
      if (isApiError(caught)) {
        const mapped: Record<string, string> = {};
        for (const param of caught.invalidParams) {
          if (param.field) mapped[param.field] = param.message;
        }
        setErrors((current) => ({ ...current, ...mapped }));
      }
    },
  });

  const submit = () => {
    setApiError(null);
    const nextErrors = validate();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    mutation.mutate({
      customerName: form.values.customerName.trim(),
      customerEmail: form.values.customerEmail.trim() || undefined,
      shippingAddress: form.values.shippingAddress.trim(),
      customerShippingCharge: parseMoneyInput(form.values.customerShippingCharge) ?? '0.00',
      items: form.values.items.map((item) => ({
        sku: item.sku,
        quantity: item.quantity as number,
        unitPrice: parseMoneyInput(item.unitPrice) ?? '0.00',
      })),
    });
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title="New manual order"
      size="xl"
      closeOnClickOutside={!mutation.isPending}
      closeOnEscape={!mutation.isPending}
    >
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          Manual orders reserve stock immediately. If any line cannot be reserved, the whole order is
          rejected with <span className="sl-code">409 INSUFFICIENT_STOCK</span> and nothing is saved.
        </Text>

        {apiError ? <ProblemAlert error={apiError} title="Manual order rejected" /> : null}

        <Grid>
          <Grid.Col span={{ base: 12, md: 6 }}>
            <TextInput
              label="Customer name"
              required
              placeholder="Freja Lindqvist"
              {...form.getInputProps('customerName')}
              error={errors.customerName}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, md: 6 }}>
            <TextInput
              label="Customer email"
              type="email"
              placeholder="freja.lindqvist@example.se"
              {...form.getInputProps('customerEmail')}
              error={errors.customerEmail}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <Textarea
              label="Shipping address"
              required
              placeholder={'Kungsgatan 14\n111 43 Stockholm\nSweden'}
              autosize
              minRows={2}
              {...form.getInputProps('shippingAddress')}
              error={errors.shippingAddress}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, md: 6 }}>
            <TextInput
              label="Shipping charge (GBP)"
              required
              placeholder="8.00"
              {...form.getInputProps('customerShippingCharge')}
              error={errors.customerShippingCharge}
            />
          </Grid.Col>
        </Grid>

        <Divider label="Items" labelPosition="left" />

        {productsQuery.isLoading ? (
          <Group>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Loading catalog…
            </Text>
          </Group>
        ) : productsQuery.isError ? (
          <ProblemAlert error={productsQuery.error} title="Catalog unavailable" />
        ) : (
          <Stack gap="sm">
            {form.values.items.map((item, index) => {
              const variant = variantBySku.get(item.sku);
              const requested = requestedBySku.get(item.sku) ?? 0;
              return (
                <Box key={item.key}>
                  <Grid align="flex-start">
                    <Grid.Col span={{ base: 12, md: 6 }}>
                      <VariantSelect
                        products={products}
                        value={item.sku || null}
                        onSelect={(selected) => {
                          updateItem(index, {
                            sku: selected?.sku ?? '',
                            unitPrice: selected
                              ? parseMoneyInput(String(selected.sellingPrice)) ?? ''
                              : item.unitPrice,
                          });
                        }}
                        label={`Item ${index + 1}`}
                        required
                        error={errors[`items.${index}.sku`]}
                      />
                      {variant ? (
                        <Text size="xs" c="dimmed" mt={4}>
                          Retail {String(variant.sellingPrice)} · {formatQuantity(variant.available)} available
                          {requested > variant.available
                            ? ` · requested ${formatQuantity(requested)}`
                            : ''}
                        </Text>
                      ) : null}
                    </Grid.Col>
                    <Grid.Col span={{ base: 6, md: 2 }}>
                      <NumberInput
                        label="Quantity"
                        min={1}
                        step={1}
                        allowDecimal={false}
                        value={item.quantity}
                        onChange={(value) =>
                          updateItem(index, { quantity: typeof value === 'number' ? value : '' })
                        }
                        error={errors[`items.${index}.quantity`]}
                        required
                      />
                    </Grid.Col>
                    <Grid.Col span={{ base: 6, md: 3 }}>
                      <TextInput
                        label="Unit price (GBP)"
                        placeholder="68.00"
                        value={item.unitPrice}
                        onChange={(event) => updateItem(index, { unitPrice: event.currentTarget.value })}
                        error={errors[`items.${index}.unitPrice`]}
                        required
                      />
                    </Grid.Col>
                    <Grid.Col span={{ base: 12, md: 1 }}>
                      <Button
                        variant="subtle"
                        color="red"
                        aria-label={`Remove item ${index + 1}`}
                        disabled={form.values.items.length === 1}
                        onClick={() => form.removeListItem('items', index)}
                        mt={{ base: 0, md: 24 }}
                      >
                        <IconTrash size={16} />
                      </Button>
                    </Grid.Col>
                  </Grid>
                </Box>
              );
            })}
            {errors.items ? (
              <Alert color="red" variant="light" icon={<IconAlertTriangle size={16} />}>
                {errors.items}
              </Alert>
            ) : null}
            <Button
              variant="light"
              leftSection={<IconPlus size={16} />}
              onClick={() => form.insertListItem('items', newItem())}
              w="fit-content"
            >
              Add item
            </Button>
          </Stack>
        )}

        <Group justify="space-between" align="flex-end" wrap="wrap">
          <Stack gap={2}>
            <Text size="xs" c="dimmed">
              Items subtotal {formatMinorUnits(subtotalMinor)}
            </Text>
            <Text fw={700} className="sl-tabular">
              Order total {formatMinorUnits(totalMinor)}
            </Text>
          </Stack>
          <Group>
            <Button variant="default" onClick={onClose} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button onClick={submit} loading={mutation.isPending}>
              Create order
            </Button>
          </Group>
        </Group>
      </Stack>
    </Modal>
  );
}
