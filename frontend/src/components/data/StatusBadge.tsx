import { Badge, Group, Text } from '@mantine/core';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconCircleX,
  IconClock,
  IconPackage,
  IconTruck,
  IconArrowBackUp,
  IconBan,
} from '@tabler/icons-react';
import type { OrderStatus } from '../../lib/api/types';
import { statusLabels } from '../../theme/tokens';

const orderConfig: Record<
  OrderStatus,
  { color: string; icon: typeof IconClock }
> = {
  PENDING: { color: 'blue', icon: IconClock },
  WAITING_FOR_STOCK: { color: 'amber', icon: IconAlertTriangle },
  READY_TO_SHIP: { color: 'teal', icon: IconPackage },
  SHIPPED: { color: 'green', icon: IconTruck },
  RETURNED: { color: 'gray', icon: IconArrowBackUp },
  PARTIALLY_RETURNED: { color: 'orange', icon: IconArrowBackUp },
  CANCELLED: { color: 'red', icon: IconBan },
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const config = orderConfig[status];
  const Icon = config.icon;
  return (
    <Badge
      color={config.color}
      variant="light"
      leftSection={<Icon size={12} aria-hidden="true" />}
      styles={{ label: { textTransform: 'none', fontWeight: 600 } }}
    >
      {statusLabels[status]}
    </Badge>
  );
}

type StockState = 'IN_STOCK' | 'LOW' | 'OUT';

function stockState(available: number, threshold: number | null): StockState {
  if (available <= 0) return 'OUT';
  if (threshold !== null && available <= threshold) return 'LOW';
  return 'IN_STOCK';
}

export function StockStatusBadge({
  available,
  threshold,
}: {
  available: number;
  threshold: number | null;
}) {
  const state = stockState(available, threshold);
  const config = {
    IN_STOCK: { color: 'green', label: 'In stock', Icon: IconCircleCheck },
    LOW: { color: 'amber', label: 'Low', Icon: IconAlertTriangle },
    OUT: { color: 'red', label: 'Out', Icon: IconCircleX },
  }[state];

  return (
    <Badge
      color={config.color}
      variant="light"
      leftSection={<config.Icon size={12} aria-hidden="true" />}
      styles={{ label: { textTransform: 'none', fontWeight: 600 } }}
    >
      {config.label}
    </Badge>
  );
}

export function InlineStockState({
  available,
  threshold,
}: {
  available: number;
  threshold: number | null;
}) {
  const state = stockState(available, threshold);
  const config = {
    IN_STOCK: { color: 'var(--sl-color-success)', label: 'In stock', Icon: IconCircleCheck },
    LOW: { color: 'var(--sl-color-warning)', label: 'Low', Icon: IconAlertTriangle },
    OUT: { color: 'var(--sl-color-danger)', label: 'Out', Icon: IconCircleX },
  }[state];
  return (
    <Group gap={4} wrap="nowrap">
      <config.Icon size={14} aria-hidden="true" color={config.color} />
      <Text size="xs" fw={600} c={config.color}>
        {config.label}
      </Text>
    </Group>
  );
}
