import { describe, expect, it } from 'vitest';
import {
  actorLabel,
  changeTitle,
  describeFields,
  describePlatformChange,
  fieldLabel,
} from './platformHistory';
import { platformEvents } from '../../test/fixtures';
import type { PlatformConnectionEvent } from '../../lib/api/types';

const [resumed, paused, edited, created] = platformEvents;

function event(overrides: Partial<PlatformConnectionEvent>): PlatformConnectionEvent {
  return { ...edited, ...overrides };
}

describe('platform history copy', () => {
  it('names each kind of change in plain words', () => {
    expect(changeTitle('CREATED')).toBe('Connection created');
    expect(changeTitle('UPDATED')).toBe('Details edited');
    expect(changeTitle('PAUSED')).toBe('Paused');
    expect(changeTitle('RESUMED')).toBe('Resumed');
    expect(changeTitle('TESTED')).toBe('Connection tested');
  });

  it('turns field names into labels an owner recognises', () => {
    expect(fieldLabel('displayName')).toBe('display name');
    expect(fieldLabel('apiEndpoint')).toBe('API address');
    expect(fieldLabel('webhookSecret')).toBe('webhook secret');
    expect(fieldLabel('apiCredentials')).toBe('API credentials');
    expect(fieldLabel('active')).toBe('status');
  });

  it('lists changed fields as a readable phrase', () => {
    expect(describeFields(['displayName'])).toBe('display name');
    expect(describeFields(['apiEndpoint', 'webhookSecret'])).toBe(
      'API address and webhook secret',
    );
    expect(describeFields(['displayName', 'apiEndpoint', 'webhookSecret'])).toBe(
      'display name, API address and webhook secret',
    );
    expect(describeFields([])).toBe('');
  });

  it('describes an edit by naming only what changed', () => {
    expect(describePlatformChange(edited)).toBe(
      'Changed API address and webhook secret.',
    );
  });

  it('shows the reason that belonged to a pause', () => {
    expect(describePlatformChange(paused)).toBe(
      'Paused because: Store closed for a seasonal break.',
    );
  });

  it('keeps a pause without a reason readable', () => {
    expect(describePlatformChange(event({ changeType: 'PAUSED', reason: null }))).toBe(
      'Paused. No reason was recorded.',
    );
  });

  it('describes creation and resume without inventing detail', () => {
    expect(describePlatformChange(created)).toBe('This connection was set up.');
    expect(describePlatformChange(resumed)).toBe('Started receiving orders again.');
  });

  it('keeps a test result readable in the history', () => {
    expect(
      describePlatformChange(
        event({
          changeType: 'TESTED',
          outcome: 'SUCCESS',
          detail: 'Connected successfully (HTTP 200).',
        }),
      ),
    ).toBe('Test passed: Connected successfully (HTTP 200).');

    expect(
      describePlatformChange(
        event({
          changeType: 'TESTED',
          outcome: 'AUTHENTICATION_FAILED',
          detail: 'The channel rejected the credentials (HTTP 401).',
        }),
      ),
    ).toBe('Test failed: The channel rejected the credentials (HTTP 401).');
  });

  it('names the person who made the change, or the system when there is none', () => {
    expect(actorLabel(edited)).toBe('mara.ellison@northwind-supply.co');
    expect(actorLabel(event({ actorEmail: null }))).toBe('StockLedger');
  });
});
