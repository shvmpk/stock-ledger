import { isApiError } from '../../lib/api/errors';
import type { PlatformCatalogEntry, PlatformType } from '../../lib/api/types';

export const WEBHOOK_ROOT = '/api/v1/webhooks/orders/';

export interface PlatformSetupValues {
  platformType: PlatformType | null;
  displayName: string;
  apiEndpoint: string;
  webhookSecret: string;
  credentials: Record<string, string>;
}

/**
 * A 192-bit secret in a copy-friendly, URL-safe form. The prefix makes it
 * recognisable in the merchant's channel settings.
 */
export function generateWebhookSecret(): string {
  const bytes = new Uint8Array(24);
  globalThis.crypto.getRandomValues(bytes);
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `whsec_${hex}`;
}

/**
 * Mirrors the server's rule so the preview shows the address that will really
 * be created. Anything outside letters and numbers becomes a single hyphen.
 */
export function slugifyWebhookAddress(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * The path the new connection will receive orders on. A custom address wins;
 * otherwise the display name is used; otherwise the platform's default.
 */
export function webhookPathFor(
  entry: PlatformCatalogEntry,
  displayName: string,
  customAddress: string,
): string {
  const slug =
    slugifyWebhookAddress(customAddress) || slugifyWebhookAddress(displayName);
  return slug ? `${WEBHOOK_ROOT}${slug}` : entry.webhookPath;
}

export function webhookUrlFor(path: string): string {
  return new URL(path, window.location.origin).toString();
}

/**
 * The address a channel uses when the merchant supplies neither a custom
 * address nor a name. It is the last segment of the catalogue's default path.
 */
export function defaultSlugOf(entry: PlatformCatalogEntry): string {
  const segments = entry.webhookPath.split('/').filter(Boolean);
  return segments[segments.length - 1] ?? entry.platformType.toLowerCase();
}

/**
 * Puts a server refusal under the input that caused it. Spring's bean
 * validation already names fields; the service-level platform errors carry a
 * code and a plain message, so they are mapped here by what they refer to.
 */
export function mapPlatformSetupErrors(
  error: unknown,
  entry: PlatformCatalogEntry | null,
): Record<string, string> {
  if (!isApiError(error)) return {};

  const mapped: Record<string, string> = {};
  for (const param of error.invalidParams) {
    if (param.message) mapped[param.field] = param.message;
  }
  if (Object.keys(mapped).length > 0) return mapped;

  const detail = error.detail;
  switch (error.code) {
    case 'INVALID_PLATFORM_ENDPOINT':
      return { apiEndpoint: detail };
    case 'PLATFORM_WEBHOOK_ADDRESS_IN_USE':
      return { slug: detail };
    case 'UNSUPPORTED_PLATFORM_TYPE':
      return { platformType: detail };
    case 'INVALID_PLATFORM_CREDENTIALS': {
      const lower = detail.toLowerCase();
      if (lower.includes('webhook secret')) return { webhookSecret: detail };
      if (lower.includes('access token')) return { accessToken: detail };
      if (lower.includes('consumer key')) return { consumerKey: detail };
      if (lower.includes('consumer secret')) return { consumerSecret: detail };
      return Object.fromEntries((entry?.credentialFields ?? []).map((field) => [field.key, detail]));
    }
    default:
      return {};
  }
}

export function validatePlatformSetup(
  values: PlatformSetupValues,
  entry: PlatformCatalogEntry | null,
): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!values.platformType) {
    errors.platformType = 'Choose a sales channel.';
  }
  if (!values.displayName.trim()) {
    errors.displayName = 'Give this connection a name so you can tell it apart.';
  }
  if (!values.apiEndpoint.trim()) {
    errors.apiEndpoint = 'Enter the API address.';
  }
  if (!values.webhookSecret.trim()) {
    errors.webhookSecret = 'Enter or generate a signing secret.';
  } else if (values.webhookSecret.length < 16) {
    errors.webhookSecret = 'The signing secret must be at least 16 characters.';
  }

  for (const field of entry?.credentialFields ?? []) {
    if (!values.credentials[field.key]?.trim()) {
      errors[field.key] = `${field.label} is required.`;
    }
  }

  return errors;
}
