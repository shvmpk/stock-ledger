import type {
  AuthEvent,
  AuthEventType,
  AuthFailureReason,
  User,
} from '../../lib/api/types';

/**
 * The default log scope deliberately excludes LOGOUT: sign-ins and security
 * changes are the events an owner opens this page to audit. "All activity"
 * removes the filter entirely.
 */
export const SECURITY_EVENT_TYPES: AuthEventType[] = [
  'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
  'PASSWORD_CHANGED',
  'PASSWORD_RESET',
  'USER_CREATED',
  'USER_DEACTIVATED',
  'USER_REACTIVATED',
  'ROLE_CHANGED',
];

export const SIGN_IN_EVENT_TYPES: AuthEventType[] = ['LOGIN_SUCCEEDED', 'LOGIN_FAILED'];

const FAILURE_COPY: Record<AuthFailureReason, string> = {
  INVALID_PASSWORD: 'wrong password',
  UNKNOWN_EMAIL: 'no account with that email',
  INACTIVE_ACCOUNT: 'account is inactive',
};

const ROLE_COPY: Record<string, string> = {
  OWNER: 'Owner',
  STAFF: 'Staff',
};

function resolveName(
  users: User[],
  userId: string | null,
  email: string | null,
): string {
  const byId = userId ? users.find((user) => user.id === userId) : undefined;
  if (byId) return byId.fullName;

  const byEmail = email
    ? users.find((user) => user.email.toLowerCase() === email.toLowerCase())
    : undefined;
  if (byEmail) return byEmail.fullName;

  return email ?? 'Unknown user';
}

function roleLabel(value: string | undefined): string {
  if (!value) return 'another role';
  return ROLE_COPY[value] ?? value;
}

/**
 * One sentence map for the whole viewer. Keeping it here (and unit-testing it)
 * stops raw enum values or UUID pairs from leaking into the UI.
 */
export function describeAuthEvent(event: AuthEvent, users: User[]): string {
  const actor = resolveName(users, event.actorUserId, event.actorEmail);
  const subject = resolveName(users, event.subjectUserId, event.subjectEmail);

  switch (event.eventType) {
    case 'LOGIN_SUCCEEDED':
      return `${actor} signed in`;
    case 'LOGIN_FAILED': {
      const reason = event.failureReason
        ? FAILURE_COPY[event.failureReason]
        : 'sign-in rejected';
      return `Failed sign-in for ${actor} — ${reason}`;
    }
    case 'LOGOUT':
      return `${actor} signed out`;
    case 'PASSWORD_CHANGED':
      return `${actor} changed their password`;
    case 'PASSWORD_RESET':
      return `${actor} reset ${subject}'s password`;
    case 'USER_CREATED':
      return `${actor} created ${subject}'s account`;
    case 'USER_DEACTIVATED':
      return `${actor} deactivated ${subject}'s account`;
    case 'USER_REACTIVATED':
      return `${actor} reactivated ${subject}'s account`;
    case 'ROLE_CHANGED': {
      const from = roleLabel(event.details.fromRole);
      const to = roleLabel(event.details.toRole);
      return `${actor} changed ${subject}'s role from ${from} to ${to}`;
    }
    default:
      return `${actor} performed a security action`;
  }
}

export function isLoopbackAddress(ipAddress: string | null): boolean {
  if (!ipAddress) return false;
  return (
    ipAddress === '::1' ||
    ipAddress === '0:0:0:0:0:0:0:1' ||
    ipAddress === '::ffff:127.0.0.1' ||
    ipAddress.startsWith('127.')
  );
}
