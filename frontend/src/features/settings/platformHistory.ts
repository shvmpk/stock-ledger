import type {
  PlatformConnectionChangeType,
  PlatformConnectionEvent,
} from '../../lib/api/types';

const FIELD_LABELS: Record<string, string> = {
  displayName: 'display name',
  apiEndpoint: 'API address',
  webhookSecret: 'webhook secret',
  apiCredentials: 'API credentials',
  active: 'status',
};

const CHANGE_TITLES: Record<PlatformConnectionChangeType, string> = {
  CREATED: 'Connection created',
  UPDATED: 'Details edited',
  PAUSED: 'Paused',
  RESUMED: 'Resumed',
  TESTED: 'Connection tested',
};

/**
 * History is read by the owner, not by the server. These helpers keep raw enum
 * values and column names out of the screen and are unit-tested so the wording
 * stays consistent everywhere the history appears.
 */
export function changeTitle(changeType: PlatformConnectionChangeType): string {
  return CHANGE_TITLES[changeType] ?? 'Connection changed';
}

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

export function describeFields(fields: string[]): string {
  const labels = fields.map(fieldLabel);
  if (labels.length === 0) return '';
  if (labels.length === 1) return labels[0];
  return `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`;
}

export function describePlatformChange(event: PlatformConnectionEvent): string {
  switch (event.changeType) {
    case 'CREATED':
      return 'This connection was set up.';
    case 'UPDATED': {
      const fields = describeFields(event.fields);
      return fields ? `Changed ${fields}.` : 'Saved without changing anything.';
    }
    case 'PAUSED':
      return event.reason
        ? `Paused because: ${event.reason}`
        : 'Paused. No reason was recorded.';
    case 'RESUMED':
      return 'Started receiving orders again.';
    case 'TESTED':
      return event.outcome === 'SUCCESS'
        ? `Test passed: ${event.detail ?? 'The channel answered.'}`
        : `Test failed: ${event.detail ?? 'The channel did not answer as expected.'}`;
    default:
      return 'Connection changed.';
  }
}

export function actorLabel(event: PlatformConnectionEvent): string {
  return event.actorEmail ?? 'StockLedger';
}
