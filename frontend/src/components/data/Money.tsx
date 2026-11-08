import { Text } from '@mantine/core';
import { formatMoney } from '../../lib/format/money';
import type { MoneyValue } from '../../lib/api/types';

export function Money({
  value,
  fw,
  c,
  size = 'sm',
}: {
  value: MoneyValue | null | undefined;
  fw?: number | string;
  c?: string;
  size?: string;
}) {
  return (
    <Text span className="sl-tabular" fw={fw} c={c} size={size}>
      {formatMoney(value)}
    </Text>
  );
}
