import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Anchor,
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconCheck, IconTruck } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listShippingRates } from '../../lib/api/endpoints/shipping';
import { shipOrder } from '../../lib/api/endpoints/orders';
import type { Carrier, Order, ParcelSize } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { useAuth } from '../../auth/useAuth';
import { formatMoney, isValidMoney, parseMoneyInput } from '../../lib/format/money';
import { carrierLabels, parcelLabels } from '../../theme/tokens';

const TRACKING_HINTS: Record<Carrier, string> = {
  DHL: 'DHL Express AWB, usually 10 digits',
  FEDEX: 'FedEx tracking number, usually 12 digits',
  UPS: 'UPS tracking number, usually starts with 1Z',
  ROYAL_MAIL: 'Royal Mail tracking reference',
};

export function DispatchModal({
  order,
  opened,
  onClose,
  onShipped,
}: {
  order: Order;
  opened: boolean;
  onClose: () => void;
  onShipped: (order: Order) => void;
}) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const ratesQuery = useQuery({
    queryKey: queryKeys.shippingRates,
    queryFn: listShippingRates,
    enabled: opened,
  });
  const [carrier, setCarrier] = useState<Carrier>(order.carrier ?? 'DHL');
  const [parcelSize, setParcelSize] = useState<ParcelSize>(order.parcelSize ?? 'MEDIUM');
  const [trackingNumber, setTrackingNumber] = useState(order.trackingNumber ?? '');
  const [override, setOverride] = useState('');
  const [error, setError] = useState<unknown>(null);

  const rates = ratesQuery.data ?? [];
  const selectedRate = rates.find(
    (rate) => rate.active && rate.carrier === carrier && rate.parcelSize === parcelSize,
  );

  const mutation = useMutation({
    mutationFn: () =>
      shipOrder(order.id, {
        carrier,
        parcelSize,
        trackingNumber: trackingNumber.trim(),
        actualShippingCostOverride: override.trim()
          ? parseMoneyInput(override)
          : null,
      }),
    onSuccess: async (updated) => {
      notifications.show({
        title: 'Order dispatched',
        message: (
          <Text size="sm">
            {updated.orderNumber} shipped with {carrierLabels[carrier]}.{' '}
            {updated.trackingUrl ? (
              <Anchor href={updated.trackingUrl} target="_blank" rel="noopener noreferrer">
                Track parcel
              </Anchor>
            ) : null}
          </Text>
        ),
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      await queryClient.invalidateQueries({ queryKey: queryKeys.order(updated.id) });
      onShipped(updated);
    },
    onError: (caught) => setError(caught),
  });

  const submit = () => {
    setError(null);
    if (!trackingNumber.trim()) {
      setError(new Error('Tracking number is required.'));
      return;
    }
    if (override.trim() && !isValidMoney(override)) {
      setError(new Error('Actual cost override must be a valid amount with up to two decimals.'));
      return;
    }
    if (!selectedRate && !override.trim()) {
      setError(new Error('No active shipping rate is configured for this carrier and parcel size.'));
      return;
    }
    mutation.mutate();
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Dispatch ${order.orderNumber}`}
      size="md"
      closeOnClickOutside={!mutation.isPending}
      closeOnEscape={!mutation.isPending}
    >
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          Shipping deducts the reserved stock, records the carrier cost, and creates a tracking link.
        </Text>

        <Group grow>
          <Select
            label="Carrier"
            value={carrier}
            onChange={(value) => setCarrier((value as Carrier) ?? 'DHL')}
            data={Object.entries(carrierLabels).map(([value, label]) => ({ value, label }))}
            allowDeselect={false}
            required
          />
          <Select
            label="Parcel size"
            value={parcelSize}
            onChange={(value) => setParcelSize((value as ParcelSize) ?? 'MEDIUM')}
            data={Object.entries(parcelLabels).map(([value, label]) => ({ value, label }))}
            allowDeselect={false}
            required
          />
        </Group>

        {ratesQuery.isError ? (
          <ProblemAlert error={ratesQuery.error} title="Shipping rates unavailable" compact />
        ) : selectedRate ? (
          <Alert color="emerald" variant="light" title="Resolved shipping cost">
            {carrierLabels[carrier]} · {parcelLabels[parcelSize]} ·{' '}
            <Text span fw={700}>
              {formatMoney(selectedRate.cost)}
            </Text>{' '}
            from your price list.
          </Alert>
        ) : (
          <Alert color="amber" variant="light" icon={<IconAlertTriangle size={16} />} title="No active rate">
            <Stack gap="xs">
              <Text size="sm">
                Configure an active {carrierLabels[carrier]} {parcelLabels[parcelSize].toLowerCase()} rate, or
                enter an actual cost override.
              </Text>
              {user?.role === 'OWNER' ? (
                <Anchor component={Link} to="/settings/rates" size="sm">
                  Open shipping rates
                </Anchor>
              ) : (
                <Text size="xs" c="dimmed">
                  Ask the store owner to configure the price list.
                </Text>
              )}
            </Stack>
          </Alert>
        )}

        <TextInput
          label="Tracking number"
          description={TRACKING_HINTS[carrier]}
          placeholder="1234567890"
          value={trackingNumber}
          onChange={(event) => setTrackingNumber(event.currentTarget.value)}
          required
          className="sl-code"
        />

        <TextInput
          label="Actual shipping cost override (GBP)"
          description="Leave blank to use the price list."
          placeholder="7.20"
          value={override}
          onChange={(event) => setOverride(event.currentTarget.value)}
        />

        {error ? <ProblemAlert error={error} title="Cannot dispatch" compact /> : null}

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            leftSection={<IconTruck size={16} />}
            loading={mutation.isPending}
            onClick={submit}
          >
            Ship order
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
