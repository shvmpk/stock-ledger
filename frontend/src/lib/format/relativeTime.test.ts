import { describe, expect, it } from 'vitest';
import { formatRelativeTime } from './relativeTime';

const now = new Date('2026-10-26T12:00:00Z');

describe('formatRelativeTime', () => {
  it('renders recent events in human units', () => {
    expect(formatRelativeTime('2026-10-26T11:59:40Z', now)).toBe('just now');
    expect(formatRelativeTime('2026-10-26T11:59:00Z', now)).toBe('1 minute ago');
    expect(formatRelativeTime('2026-10-26T11:48:00Z', now)).toBe('12 minutes ago');
    expect(formatRelativeTime('2026-10-26T11:00:00Z', now)).toBe('1 hour ago');
    expect(formatRelativeTime('2026-10-26T09:00:00Z', now)).toBe('3 hours ago');
    expect(formatRelativeTime('2026-10-25T12:00:00Z', now)).toBe('1 day ago');
    expect(formatRelativeTime('2026-10-19T12:00:00Z', now)).toBe('7 days ago');
  });

  it('falls back to a calendar date beyond the relative window', () => {
    expect(formatRelativeTime('2026-08-14T09:30:00Z', now)).toBe('14 Aug 2026');
  });

  it('never shows a future timestamp as negative', () => {
    expect(formatRelativeTime('2026-10-26T12:05:00Z', now)).toBe('just now');
  });

  it('renders an em dash for missing or invalid values', () => {
    expect(formatRelativeTime(null, now)).toBe('—');
    expect(formatRelativeTime('not-a-date', now)).toBe('—');
  });
});
