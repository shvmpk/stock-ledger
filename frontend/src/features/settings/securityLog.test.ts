import { describe, expect, it } from 'vitest';
import { describeAuthEvent, isLoopbackAddress } from './securityLog';
import { authEvents, ownerUser, staffUser } from '../../test/fixtures';
import type { AuthEvent } from '../../lib/api/types';

const directory = [ownerUser, staffUser];

function eventOfType(type: AuthEvent['eventType']): AuthEvent {
  const event = authEvents.find((entry) => entry.eventType === type);
  if (!event) throw new Error(`Missing fixture for ${type}`);
  return event;
}

describe('describeAuthEvent', () => {
  it('describes a successful sign-in with the resolved full name', () => {
    expect(describeAuthEvent(eventOfType('LOGIN_SUCCEEDED'), directory)).toBe(
      'Mara Ellison signed in',
    );
  });

  it('explains why a sign-in failed without leaking a raw enum value', () => {
    expect(describeAuthEvent(eventOfType('LOGIN_FAILED'), directory)).toBe(
      'Failed sign-in for Theo Marsh — wrong password',
    );

    const unknownAttempt: AuthEvent = {
      ...eventOfType('LOGIN_FAILED'),
      actorUserId: null,
      actorEmail: 'deliveries@northwind-supply.co',
      failureReason: 'UNKNOWN_EMAIL',
    };
    expect(describeAuthEvent(unknownAttempt, directory)).toBe(
      'Failed sign-in for deliveries@northwind-supply.co — no account with that email',
    );

    const inactive: AuthEvent = {
      ...eventOfType('LOGIN_FAILED'),
      failureReason: 'INACTIVE_ACCOUNT',
    };
    expect(describeAuthEvent(inactive, directory)).toContain('account is inactive');
  });

  it('names both the actor and the account owner on a password reset', () => {
    expect(describeAuthEvent(eventOfType('PASSWORD_RESET'), directory)).toBe(
      "Mara Ellison reset Theo Marsh's password",
    );
  });

  it('describes password changes, account administration, and role changes', () => {
    expect(describeAuthEvent(eventOfType('PASSWORD_CHANGED'), directory)).toBe(
      'Theo Marsh changed their password',
    );
    expect(describeAuthEvent(eventOfType('USER_CREATED'), directory)).toBe(
      "Mara Ellison created Theo Marsh's account",
    );
    expect(describeAuthEvent(eventOfType('ROLE_CHANGED'), directory)).toBe(
      "Mara Ellison changed Theo Marsh's role from Staff to Owner",
    );
    expect(describeAuthEvent(eventOfType('LOGOUT'), directory)).toBe('Mara Ellison signed out');
  });

  it('falls back to the snapshotted email when the user row is gone', () => {
    const deletedUser: AuthEvent = {
      ...eventOfType('PASSWORD_RESET'),
      actorUserId: null,
      actorEmail: 'ingrid.bergstrom@stockledger.io',
    };
    expect(describeAuthEvent(deletedUser, directory)).toBe(
      "ingrid.bergstrom@stockledger.io reset Theo Marsh's password",
    );
  });
});

describe('isLoopbackAddress', () => {
  it('recognises IPv4, IPv6, and mapped loopback addresses', () => {
    expect(isLoopbackAddress('127.0.0.1')).toBe(true);
    expect(isLoopbackAddress('::1')).toBe(true);
    expect(isLoopbackAddress('0:0:0:0:0:0:0:1')).toBe(true);
    expect(isLoopbackAddress('::ffff:127.0.0.1')).toBe(true);
    expect(isLoopbackAddress('203.0.113.42')).toBe(false);
    expect(isLoopbackAddress(null)).toBe(false);
  });
});
