import { Text } from '@mantine/core';
import { formatDelta } from '../../lib/format/quantity';

export function Delta({
  value,
  label,
  showZero = true,
}: {
  value: number;
  label?: string;
  showZero?: boolean;
}) {
  if (!showZero && value === 0) {
    return (
      <Text span c="dimmed" className="sl-tabular">
        —
      </Text>
    );
  }
  const color = value > 0 ? 'var(--sl-color-success)' : value < 0 ? 'var(--sl-color-danger)' : 'dimmed';
  return (
    <Text span className="sl-tabular" c={color} fw={value === 0 ? 400 : 600}>
      <span aria-label={label ? `${label}: ${formatDelta(value)}` : undefined}>{formatDelta(value)}</span>
    </Text>
  );
}
