const quantityFormatter = new Intl.NumberFormat('en-GB', {
  maximumFractionDigits: 0,
});

export function formatQuantity(value: number): string {
  return quantityFormatter.format(value);
}

export function formatDelta(value: number): string {
  if (value === 0) return '0';
  const sign = value > 0 ? '+' : '−';
  return `${sign}${quantityFormatter.format(Math.abs(value))}`;
}

export function varianceLabel(variance: number): string {
  if (variance === 0) return 'Match';
  return variance > 0 ? `Over ${formatQuantity(variance)}` : `Short ${formatQuantity(Math.abs(variance))}`;
}

export function shortfall(available: number, threshold: number): number {
  return Math.max(0, threshold - available);
}
