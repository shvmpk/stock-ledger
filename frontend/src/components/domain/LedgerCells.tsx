import { Group, Stack, Text } from '@mantine/core';
import { formatQuantity } from '../../lib/format/quantity';
import { InlineStockState } from '../data/StatusBadge';

export function LedgerNumbers({
  onHand,
  reserved,
  available,
  threshold,
}: {
  onHand: number;
  reserved: number;
  available: number;
  threshold: number | null;
}) {
  return (
    <Stack gap={2}>
      <Group gap="lg" wrap="nowrap" justify="flex-end">
        <Text span size="sm" className="sl-tabular" w={52} ta="right">
          {formatQuantity(onHand)}
        </Text>
        <Text span size="sm" className="sl-tabular" w={52} ta="right">
          {formatQuantity(reserved)}
        </Text>
        <Text span size="sm" fw={700} className="sl-tabular" w={52} ta="right">
          {formatQuantity(available)}
        </Text>
        <InlineStockState available={available} threshold={threshold} />
      </Group>
      <Text className="sl-ledger-caption" ta="right">
        Available = {formatQuantity(onHand)} − {formatQuantity(reserved)}
      </Text>
    </Stack>
  );
}

export function LedgerEquation({
  onHand,
  reserved,
  available,
}: {
  onHand: number;
  reserved: number;
  available: number;
}) {
  return (
    <Text className="sl-equation">
      Available = On hand − Reserved · {formatQuantity(onHand)} − {formatQuantity(reserved)} ={' '}
      <Text span fw={700} className="sl-tabular">
        {formatQuantity(available)}
      </Text>
    </Text>
  );
}
