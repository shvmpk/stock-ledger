import { describe, expect, it } from 'vitest';
import { formatMargin, formatMoney, marginPercent } from './money';

describe('money formatting', () => {
  it('formats a present amount and a missing amount', () => {
    expect(formatMoney('24.00')).toBe('£24.00');
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('calculates margin from cost and retail price', () => {
    expect(marginPercent('24.00', '58.00')).toBeCloseTo(58.62, 2);
    expect(formatMargin('24.00', '58.00')).toBe('58.6%');
  });

  it('treats a zero cost as a real cost, not a withheld one', () => {
    expect(formatMargin(0, '58.00')).toBe('100.0%');
  });

  it('shows an em dash when cost is withheld', () => {
    expect(marginPercent(null, '58.00')).toBeNull();
    expect(marginPercent(undefined, '58.00')).toBeNull();
    expect(formatMargin(null, '58.00')).toBe('—');
    expect(formatMargin(undefined, '58.00')).toBe('—');
    expect(formatMargin('', '58.00')).toBe('—');
  });

  it('shows an em dash when the retail price cannot produce a margin', () => {
    expect(formatMargin('24.00', '0.00')).toBe('—');
  });
});
