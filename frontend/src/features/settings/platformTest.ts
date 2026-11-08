import type { PlatformTestOutcome } from '../../lib/api/types';
import { formatDateTime } from '../../lib/format/date';

const OUTCOME_LABELS: Record<PlatformTestOutcome, string> = {
  SUCCESS: 'Connected',
  AUTHENTICATION_FAILED: 'Authentication failed',
  FAILED: 'Test failed',
  UNREACHABLE: 'Unreachable',
  AWAITING_INBOUND: 'Awaiting first inbound order',
  NOT_CONFIGURED: 'Credentials missing',
};

const OUTCOME_COLORS: Record<PlatformTestOutcome, string> = {
  SUCCESS: 'green',
  AUTHENTICATION_FAILED: 'red',
  FAILED: 'red',
  UNREACHABLE: 'red',
  AWAITING_INBOUND: 'gray',
  NOT_CONFIGURED: 'amber',
};

/**
 * The result of a connection test is shown to the owner, not to a developer.
 * These helpers keep raw enum values off the screen and are unit-tested so the
 * wording stays the same on the card and in history.
 */
export function testOutcomeLabel(outcome: PlatformTestOutcome): string {
  return OUTCOME_LABELS[outcome] ?? 'Test finished';
}

export function testOutcomeColor(outcome: PlatformTestOutcome): string {
  return OUTCOME_COLORS[outcome] ?? 'gray';
}

export function describeInboundStatus(lastInboundOrderAt: string | null): string {
  if (!lastInboundOrderAt) {
    return 'Awaiting first inbound order.';
  }
  return `Last order received ${formatDateTime(lastInboundOrderAt)}.`;
}
