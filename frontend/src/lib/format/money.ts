import type { MoneyValue } from '../api/types';

const gbp = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function toMinorUnits(value: MoneyValue | null | undefined): number {
  if (value === null || value === undefined || value === '') return 0;
  const numeric = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(numeric)) return 0;
  return Math.round(numeric * 100);
}

export function formatMinorUnits(minorUnits: number): string {
  return gbp.format(minorUnits / 100);
}

export function formatMoney(value: MoneyValue | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  return formatMinorUnits(toMinorUnits(value));
}

export function lineTotal(unitPrice: MoneyValue, quantity: number): number {
  return toMinorUnits(unitPrice) * quantity;
}

export function sumMoney(values: Array<MoneyValue | null | undefined>): number {
  return values.reduce<number>((total, value) => total + toMinorUnits(value), 0);
}

export function parseMoneyInput(value: string): string | null {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  if (!/^\d+(\.\d{0,2})?$/.test(trimmed)) return null;
  return Number(trimmed).toFixed(2);
}

export function isValidMoney(value: string): boolean {
  return parseMoneyInput(value) !== null;
}

export function marginPercent(
  cost: MoneyValue | null | undefined,
  selling: MoneyValue,
): number | null {
  if (cost === null || cost === undefined || cost === '') return null;
  const sellingMinor = toMinorUnits(selling);
  if (sellingMinor <= 0) return null;
  const costMinor = toMinorUnits(cost);
  return ((sellingMinor - costMinor) / sellingMinor) * 100;
}

export function formatMargin(
  cost: MoneyValue | null | undefined,
  selling: MoneyValue,
): string {
  const margin = marginPercent(cost, selling);
  if (margin === null) return '—';
  return `${margin.toFixed(1)}%`;
}
