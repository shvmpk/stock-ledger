import { Route, Routes } from 'react-router-dom';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { AddPlatformPage } from './AddPlatformPage';
import { PlatformsPage } from './PlatformsPage';
import { renderWithProviders } from '../../test/render';
import { platforms } from '../../test/fixtures';
import { server } from '../../test/server';

const SHOPIFY_ENDPOINT = 'https://northwind-supply.myshopify.com/admin/api/2026-10';
const SHOPIFY_ACCESS_TOKEN = 'dummy-shopify-access-token';
const WOOCOMMERCE_ENDPOINT = 'https://northwind-home.co.uk/wp-json/wc/v3';
const WOOCOMMERCE_CONSUMER_KEY = 'test-woocommerce-consumer-key';
const WOOCOMMERCE_CONSUMER_SECRET = 'dummy-woocommerce-secret';

function renderAddPlatform() {
  return renderWithProviders(
    <Routes>
      <Route path="/settings/platforms/new" element={<AddPlatformPage />} />
      <Route path="/settings/platforms" element={<PlatformsPage />} />
    </Routes>,
    { route: '/settings/platforms/new' },
  );
}

async function chooseShopify(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { name: 'Connect a platform' });
  await user.click(await screen.findByRole('radio', { name: /Shopify/ }));
}

async function fillShopifyForm(user: ReturnType<typeof userEvent.setup>) {
  await chooseShopify(user);
  await user.type(screen.getByRole('textbox', { name: 'Connection name' }), 'Northwind Supply EU');
  await user.type(screen.getByRole('textbox', { name: 'API address' }), SHOPIFY_ENDPOINT);
  await user.type(screen.getByLabelText(/^Access token/), SHOPIFY_ACCESS_TOKEN);
  await user.click(screen.getByRole('button', { name: 'Generate secret' }));
}

function problem(
  status: number,
  code: string,
  detail: string,
  invalidParams: Array<{ field: string; message: string }> = [],
) {
  return HttpResponse.json(
    {
      type: `https://api.stockledger.io/errors/${code.toLowerCase().replace(/_/g, '-')}`,
      title: 'Request refused',
      status,
      detail,
      code,
      timestamp: '2026-10-28T10:00:00Z',
      invalidParams,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

describe('AddPlatformPage', () => {
  it('lists the connectable channels and explains why Etsy is disabled', async () => {
    renderAddPlatform();

    expect(await screen.findByRole('heading', { name: 'Connect a platform' })).toBeInTheDocument();
    expect(await screen.findByRole('radio', { name: /Shopify/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /WooCommerce/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /TikTok Shop/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /Square POS/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /Custom Channel/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /Etsy/ })).toBeDisabled();
    expect(
      screen.getByText(/Etsy only delivers order notifications to approved applications/),
    ).toBeInTheDocument();
  });

  it('asks only for the credentials the chosen channel needs and shows its API example', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await chooseShopify(user);

    expect(screen.getByLabelText(/^Access token/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Consumer key/)).not.toBeInTheDocument();
    expect(screen.getByText(SHOPIFY_ENDPOINT.replace('northwind-supply', 'your-store'))).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /WooCommerce/ }));
    expect(screen.getByLabelText(/^Consumer key/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Consumer secret/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Access token/)).not.toBeInTheDocument();
    expect(screen.getByText('https://your-store.com/wp-json/wc/v3')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /Custom Channel/ }));
    expect(screen.getByText(/No API credentials needed/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Access token/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Consumer key/)).not.toBeInTheDocument();
  });

  it('previews the webhook URL from the connection name or a custom address', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await chooseShopify(user);

    await user.type(screen.getByRole('textbox', { name: 'Connection name' }), 'Northwind Supply EU');
    await user.click(screen.getByRole('button', { name: /Advanced/ }));

    expect(
      screen.getByText(`${window.location.origin}/api/v1/webhooks/orders/northwind-supply-eu`),
    ).toBeInTheDocument();

    const customAddress = screen.getByLabelText(/Custom webhook address/);
    await user.clear(customAddress);
    await user.type(customAddress, 'Northwind EU');

    expect(
      screen.getByText(`${window.location.origin}/api/v1/webhooks/orders/northwind-eu`),
    ).toBeInTheDocument();
  });

  it('reveals and hides the signing secret on request', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await chooseShopify(user);

    const secret = screen.getByLabelText(/^Signing secret/);
    expect(secret).toHaveAttribute('type', 'password');

    await user.type(secret, 'dummy-webhook-secret');
    await user.click(screen.getByRole('button', { name: 'Show signing secret' }));
    expect(secret).toHaveAttribute('type', 'text');

    await user.click(screen.getByRole('button', { name: 'Hide signing secret' }));
    expect(secret).toHaveAttribute('type', 'password');
  });

  it('connects a Shopify store, shows the secret once, then shows the new card without it', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await fillShopifyForm(user);

    const secret = (screen.getByLabelText(/^Signing secret/) as HTMLInputElement).value;
    expect(secret).toMatch(/^whsec_[0-9a-f]{48}$/);

    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    expect(await screen.findByRole('heading', { name: 'Platform connected' })).toBeInTheDocument();
    expect(
      screen.getByText(`${window.location.origin}/api/v1/webhooks/orders/northwind-supply-eu`),
    ).toBeInTheDocument();
    expect(screen.getByText(secret)).toBeInTheDocument();
    expect(screen.getByText(/cannot be shown again/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Done' }));

    expect(await screen.findByRole('heading', { name: 'Platforms' })).toBeInTheDocument();
    expect(await screen.findByText('/api/v1/webhooks/orders/northwind-supply-eu')).toBeInTheDocument();
    expect(screen.getByText('Not synced yet')).toBeInTheDocument();
    expect(screen.queryByText(secret)).not.toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /Add platform/ }));
    expect(await screen.findByRole('heading', { name: 'Connect a platform' })).toBeInTheDocument();
    expect((screen.getByLabelText(/^Signing secret/) as HTMLInputElement).value).toBe('');
    expect(screen.queryByText(secret)).not.toBeInTheDocument();
  });

  it('puts validation errors from the server under the fields that caused them', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/v1/platforms', () =>
        problem(400, 'VALIDATION_FAILED', 'One or more fields failed validation constraints.', [
          { field: 'apiEndpoint', message: 'API endpoint must use HTTPS in production.' },
          {
            field: 'webhookSecret',
            message: 'Webhook secret must be between 16 and 512 characters.',
          },
        ]),
      ),
    );

    renderAddPlatform();
    await fillShopifyForm(user);
    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    expect(
      await screen.findByText('API endpoint must use HTTPS in production.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Webhook secret must be between 16 and 512 characters.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'API address' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('puts a service-level endpoint refusal under the API address', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/v1/platforms', () =>
        problem(
          400,
          'INVALID_PLATFORM_ENDPOINT',
          'Platform endpoint must not target a private or local address.',
        ),
      ),
    );

    renderAddPlatform();
    await fillShopifyForm(user);
    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    expect(
      await screen.findByText('Platform endpoint must not target a private or local address.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'API address' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('keeps the same request key when a failed submit is retried', async () => {
    const user = userEvent.setup();
    const keys: Array<string | null> = [];
    let attempts = 0;

    server.use(
      http.post('/api/v1/platforms', async ({ request }) => {
        attempts += 1;
        keys.push(request.headers.get('Idempotency-Key'));
        if (attempts === 1) {
          return problem(500, 'INTERNAL_SERVER_ERROR', 'An unexpected internal error occurred.');
        }
        return HttpResponse.json(
          {
            success: true,
            data: {
              ...platforms[0],
              id: '00000000-0000-4000-8000-000000000902',
              displayName: 'Northwind Supply EU',
              apiEndpoint: SHOPIFY_ENDPOINT,
              webhookPath: '/api/v1/webhooks/orders/northwind-supply-eu',
              syncStatus: 'PENDING',
              lastSyncAt: null,
              lastErrorMessage: null,
              version: 0,
            },
            meta: { timestamp: '2026-10-28T10:00:00Z' },
          },
          { status: 201 },
        );
      }),
    );

    renderAddPlatform();
    await fillShopifyForm(user);

    await user.click(screen.getByRole('button', { name: 'Connect platform' }));
    expect(await screen.findByText('An unexpected internal error occurred.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Connect platform' }));
    expect(await screen.findByRole('heading', { name: 'Platform connected' })).toBeInTheDocument();

    expect(keys).toHaveLength(2);
    expect(keys[0]).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(keys[1]).toBe(keys[0]);
  });

  it('asks for WooCommerce consumer credentials and sends only those', async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> | null = null;

    server.use(
      http.post('/api/v1/platforms', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          {
            success: true,
            data: {
              ...platforms[1],
              id: '00000000-0000-4000-8000-000000000903',
              displayName: 'Northwind Home',
              apiEndpoint: WOOCOMMERCE_ENDPOINT,
              webhookPath: '/api/v1/webhooks/orders/northwind-home',
              syncStatus: 'PENDING',
              lastSyncAt: null,
              lastErrorMessage: null,
              version: 0,
            },
            meta: { timestamp: '2026-10-28T10:00:00Z' },
          },
          { status: 201 },
        );
      }),
    );

    renderAddPlatform();
    await screen.findByRole('heading', { name: 'Connect a platform' });
    await user.click(await screen.findByRole('radio', { name: /WooCommerce/ }));
    await user.type(screen.getByRole('textbox', { name: 'Connection name' }), 'Northwind Home');
    await user.type(screen.getByRole('textbox', { name: 'API address' }), WOOCOMMERCE_ENDPOINT);
    await user.type(screen.getByLabelText(/^Consumer key/), WOOCOMMERCE_CONSUMER_KEY);
    await user.type(screen.getByLabelText(/^Consumer secret/), WOOCOMMERCE_CONSUMER_SECRET);
    await user.click(screen.getByRole('button', { name: 'Generate secret' }));
    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    expect(await screen.findByRole('heading', { name: 'Platform connected' })).toBeInTheDocument();
    expect(body).toMatchObject({
      platformType: 'WOOCOMMERCE',
      displayName: 'Northwind Home',
      apiEndpoint: WOOCOMMERCE_ENDPOINT,
      consumerKey: WOOCOMMERCE_CONSUMER_KEY,
      consumerSecret: WOOCOMMERCE_CONSUMER_SECRET,
    });
    expect(body).not.toHaveProperty('accessToken');
  });

  it('explains missing fields in plain English instead of leaving the browser to complain', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await chooseShopify(user);

    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    expect(
      await screen.findByText('Give this connection a name so you can tell it apart.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Enter the API address.')).toBeInTheDocument();
    expect(screen.getByText('Access token is required.')).toBeInTheDocument();
    expect(screen.getByText('Enter or generate a signing secret.')).toBeInTheDocument();
  });

  it('shows the signing-secret warning in the confirmation panel', async () => {
    const user = userEvent.setup();
    renderAddPlatform();
    await fillShopifyForm(user);
    await user.click(screen.getByRole('button', { name: 'Connect platform' }));

    const panel = await screen.findByRole('region', { name: 'Connection details' });
    expect(
      within(panel).getByText(/The signing secret cannot be shown again/i),
    ).toBeInTheDocument();
  });
});
