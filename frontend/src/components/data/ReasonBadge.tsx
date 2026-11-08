import { Badge } from '@mantine/core';
import type { StockMovementReason } from '../../lib/api/types';
import { movementReasonLabels } from '../../theme/tokens';

const colors: Record<StockMovementReason, string> = {
  RECEIVE: 'green',
  ORDER_RESERVED: 'blue',
  ORDER_SHIPPED: 'teal',
  ORDER_CANCELLED: 'gray',
  RETURN_RESTOCK: 'lime',
  RETURN_DAMAGED: 'orange',
  AUDIT_CORRECTION: 'violet',
};

export function ReasonBadge({ reason }: { reason: StockMovementReason }) {
  return (
    <Badge
      color={colors[reason]}
      variant="light"
      styles={{ label: { textTransform: 'none', fontWeight: 600 } }}
    >
      {movementReasonLabels[reason]}
    </Badge>
  );
}
