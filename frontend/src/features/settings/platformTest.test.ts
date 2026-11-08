import { describe, expect, it } from 'vitest';
import {
  describeInboundStatus,
  testOutcomeColor,
  testOutcomeLabel,
} from './platformTest';

describe('connection test copy', () => {
  it('names every test result in plain words', () => {
    expect(testOutcomeLabel('SUCCESS')).toBe('Connected');
    expect(testOutcomeLabel('AUTHENTICATION_FAILED')).toBe('Authentication failed');
    expect(testOutcomeLabel('FAILED')).toBe('Test failed');
    expect(testOutcomeLabel('UNREACHABLE')).toBe('Unreachable');
    expect(testOutcomeLabel('AWAITING_INBOUND')).toBe('Awaiting first inbound order');
    expect(testOutcomeLabel('NOT_CONFIGURED')).toBe('Credentials missing');
  });

  it('colours a success green and a refusal red', () => {
    expect(testOutcomeColor('SUCCESS')).toBe('green');
    expect(testOutcomeColor('AUTHENTICATION_FAILED')).toBe('red');
    expect(testOutcomeColor('FAILED')).toBe('red');
    expect(testOutcomeColor('UNREACHABLE')).toBe('red');
    expect(testOutcomeColor('AWAITING_INBOUND')).toBe('gray');
    expect(testOutcomeColor('NOT_CONFIGURED')).toBe('amber');
  });

  it('says plainly when an inbound channel is still waiting', () => {
    expect(describeInboundStatus(null)).toBe('Awaiting first inbound order.');
  });

  it('shows when an inbound channel last received an order', () => {
    expect(describeInboundStatus('2026-10-24T08:15:00Z')).toBe(
      'Last order received 24 Oct 2026, 08:15.',
    );
  });
});
