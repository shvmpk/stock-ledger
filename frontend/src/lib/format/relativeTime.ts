import { formatDate } from './date';

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

const relativeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'always' });

/**
 * Relative labels are for scanning. The exact local timestamp stays available
 * as a tooltip, and anything older than a month falls back to a calendar date
 * so the log never says "37 days ago".
 */
export function formatRelativeTime(
  value: string | null | undefined,
  now: Date = new Date(),
): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';

  const diffMs = now.getTime() - date.getTime();
  if (diffMs < 45_000) return 'just now';

  if (diffMs < DAY_MS) {
    const minutes = Math.round(diffMs / MINUTE_MS);
    if (minutes < 60) return relativeFormatter.format(-minutes, 'minute');
    return relativeFormatter.format(-Math.round(diffMs / HOUR_MS), 'hour');
  }

  const days = Math.round(diffMs / DAY_MS);
  if (days <= 30) return relativeFormatter.format(-days, 'day');

  return formatDate(value);
}
