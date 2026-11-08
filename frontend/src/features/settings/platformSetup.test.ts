import { describe, expect, it } from 'vitest';
import {
  generateWebhookSecret,
  mapPlatformSetupErrors,
  slugifyWebhookAddress,
  validatePlatformSetup,
  webhookPathFor,
} from './platformSetup';
import { ApiError } from '../../lib/api/errors';
import type { PlatformType } from '../../lib/api/types';
import { platformCatalog } from '../../test/fixtures';

const shopify = platformCatalog.find((entry) => entry.platformType === 'SHOPIFY')!;
const wooCommerce = platformCatalog.find((entry) => entry.platformType === 'WOOCOMMERCE')!;
const customChannel = platformCatalog.find((entry) => entry.platformType === 'CUSTOM_WEBHOOK')!;

function apiError(status: number, problem: Record<string, unknown>): ApiError {
  return new ApiError(status, problem);
}

function form(overrides: Partial<Parameters<typeof validatePlatformSetup>[0]> = {}) {
  return {
    platformType: 'SHOPIFY' as PlatformType | null,
    displayName: 'Northwind Supply EU',
    apiEndpoint: 'https://northwind-supply.myshopify.com/admin/api/2026-10',
    webhookSecret: 'dummy-webhook-secret',
    credentials: { accessToken: 'dummy-shopify-access-token' },
    ...overrides,
  };
}

describe('platform setup helpers', () => {
  it('generates a high-entropy signing secret with the expected prefix', () => {
    const first = generateWebhookSecret();
    const second = generateWebhookSecret();

    expect(first).toMatch(/^whsec_[0-9a-f]{48}$/);
    expect(second).toMatch(/^whsec_[0-9a-f]{48}$/);
    expect(first).not.toBe(second);
  });

  it('turns a merchant-friendly name into the same webhook address the server would use', () => {
    expect(slugifyWebhookAddress('Northwind Supply EU')).toBe('northwind-supply-eu');
    expect(slugifyWebhookAddress('  Harbour & Pine  ')).toBe('harbour-pine');
    expect(slugifyWebhookAddress('already-safe')).toBe('already-safe');
    expect(slugifyWebhookAddress('!!!')).toBe('');
  });

  it('previews the webhook path from the name, a custom address, or the platform default', () => {
    expect(webhookPathFor(shopify, 'Northwind Supply EU', '')).toBe(
      '/api/v1/webhooks/orders/northwind-supply-eu',
    );
    expect(webhookPathFor(shopify, 'Northwind Supply EU', 'Northwind EU')).toBe(
      '/api/v1/webhooks/orders/northwind-eu',
    );
    expect(webhookPathFor(shopify, '', '')).toBe(shopify.webhookPath);
  });

  it('places server field errors against the field that caused them', () => {
    expect(
      mapPlatformSetupErrors(
        apiError(400, {
          code: 'VALIDATION_FAILED',
          detail: 'One or more fields failed validation constraints.',
          invalidParams: [
            { field: 'apiEndpoint', message: 'API endpoint is required.' },
            { field: 'webhookSecret', message: 'Webhook secret must be between 16 and 512 characters.' },
          ],
        }),
        shopify,
      ),
    ).toEqual({
      apiEndpoint: 'API endpoint is required.',
      webhookSecret: 'Webhook secret must be between 16 and 512 characters.',
    });

    expect(
      mapPlatformSetupErrors(
        apiError(400, {
          code: 'INVALID_PLATFORM_ENDPOINT',
          detail: 'Platform endpoint must use HTTPS in production.',
        }),
        shopify,
      ),
    ).toEqual({ apiEndpoint: 'Platform endpoint must use HTTPS in production.' });

    expect(
      mapPlatformSetupErrors(
        apiError(400, {
          code: 'INVALID_PLATFORM_CREDENTIALS',
          detail: 'Shopify requires a non-blank access token.',
        }),
        shopify,
      ),
    ).toEqual({ accessToken: 'Shopify requires a non-blank access token.' });

    expect(
      mapPlatformSetupErrors(
        apiError(400, {
          code: 'INVALID_PLATFORM_CREDENTIALS',
          detail: 'Webhook secret must be between 16 and 512 characters.',
        }),
        shopify,
      ),
    ).toEqual({ webhookSecret: 'Webhook secret must be between 16 and 512 characters.' });

    expect(
      mapPlatformSetupErrors(
        apiError(409, {
          code: 'PLATFORM_WEBHOOK_ADDRESS_IN_USE',
          detail: 'Another active connection already receives orders at /api/v1/webhooks/orders/shopify.',
        }),
        shopify,
      ),
    ).toEqual({
      slug: 'Another active connection already receives orders at /api/v1/webhooks/orders/shopify.',
    });
  });

  it('does not invent field errors for an unrelated problem', () => {
    expect(
      mapPlatformSetupErrors(
        apiError(500, { code: 'INTERNAL_SERVER_ERROR', detail: 'An unexpected internal error occurred.' }),
        shopify,
      ),
    ).toEqual({});
  });

  it('asks for the name, address, secret, and every credential the channel needs', () => {
    expect(
      validatePlatformSetup(
        form({
          platformType: null,
          displayName: '   ',
          apiEndpoint: '',
          webhookSecret: '',
          credentials: {},
        }),
        shopify,
      ),
    ).toEqual({
      platformType: 'Choose a sales channel.',
      displayName: 'Give this connection a name so you can tell it apart.',
      apiEndpoint: 'Enter the API address.',
      webhookSecret: 'Enter or generate a signing secret.',
      accessToken: 'Access token is required.',
    });

    expect(validatePlatformSetup(form(), shopify)).toEqual({});
    expect(
      validatePlatformSetup(form({ webhookSecret: 'too-short' }), shopify),
    ).toEqual({ webhookSecret: 'The signing secret must be at least 16 characters.' });
  });

  it('only asks WooCommerce for its consumer key and secret', () => {
    expect(
      validatePlatformSetup(
        form({ platformType: 'WOOCOMMERCE', credentials: { consumerKey: 'ck_5f2a9c1e7b4d8360' } }),
        wooCommerce,
      ),
    ).toEqual({ consumerSecret: 'Consumer secret is required.' });
  });

  it('asks inbound-only channels for no API credentials', () => {
    expect(
      validatePlatformSetup(
        form({ platformType: 'CUSTOM_WEBHOOK', credentials: {} }),
        customChannel,
      ),
    ).toEqual({});
  });
});
