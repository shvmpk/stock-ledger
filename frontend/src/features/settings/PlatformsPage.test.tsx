import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { PlatformsPage } from './PlatformsPage';
import { platforms } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import type { PlatformConnection } from '../../lib/api/types';

const PAUSE_REASON = 'Store closed for a seasonal break.';

const pausedShopify: PlatformConnection = {
  ...platforms[0],
  active: false,
  deactivatedAt: '2026-10-23T17:40:00Z',
  pauseReason: PAUSE_REASON,
};

async function openActionsMenu(
  user: ReturnType<typeof userEvent.setup>,
  platformName: string,
): Promise<HTMLElement> {
  await user.click(screen.getByRole('button', { name: `Actions for ${platformName}` }));
  return screen.findByRole('menu', { hidden: true });
}

function chooseMenuItem(menu: HTMLElement, name: string) {
  fireEvent.click(within(menu).getByRole('menuitem', { name, hidden: true }));
}

describe('PlatformsPage', () => {
  it('renders the seeded Shopify and WooCommerce connections with their sync state', async () => {
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });

    expect(await screen.findByRole('heading', { name: 'Platforms' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Shopify' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'WooCommerce' })).toBeInTheDocument();
    expect(screen.getByText('https://northwind-supply.myshopify.com/admin/api/2026-10')).toBeInTheDocument();
    expect(screen.getByText('https://northwind-home.co.uk/wp-json/wc/v3')).toBeInTheDocument();

    const summary = screen.getByLabelText('Platform connection summary');
    expect(summary).toHaveTextContent('2');
    expect(summary).toHaveTextContent('Connections');
    expect(summary).toHaveTextContent('Needs attention');

    expect(screen.getByText('Last sync failed')).toBeInTheDocument();
    expect(
      screen.getByText('Authentication failed (HTTP 401). Check platform credentials.'),
    ).toBeInTheDocument();
    expect(screen.getByText(/Inbound webhooks cryptographically verify HMAC-SHA256 signatures/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Add platform/ })).toHaveAttribute(
      'href',
      '/settings/platforms/new',
    );
  });

  it('syncs WooCommerce and clears the failed connection error', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'WooCommerce' });

    const syncButtons = screen.getAllByRole('button', { name: 'Sync now' });
    expect(syncButtons).toHaveLength(2);
    await user.click(syncButtons[1]);

    expect(await screen.findByText('Platform sync finished')).toBeInTheDocument();
    expect(
      screen.getByText('Imported orders are now in the New or Waiting for stock queues.'),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.queryByText('Authentication failed (HTTP 401). Check platform credentials.'),
      ).not.toBeInTheDocument(),
    );
    expect(screen.queryByText('Last sync failed')).not.toBeInTheDocument();

    const summary = screen.getByLabelText('Platform connection summary');
    expect(within(summary).getByText('0')).toBeInTheDocument();
  });

  it('titles each connection with the platform name and keeps the merchant label as a badge', async () => {
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });

    expect(await screen.findByRole('heading', { name: 'Shopify' })).toBeInTheDocument();
    expect(screen.getByText('Shopify Store')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'WooCommerce' })).toBeInTheDocument();
    expect(screen.getByText('WooCommerce Store')).toBeInTheDocument();
  });

  it('offers edit, pause and history from each connection menu', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });

    const menu = await openActionsMenu(user, 'Shopify');

    expect(within(menu).getByRole('menuitem', { name: 'Edit connection', hidden: true })).toBeInTheDocument();
    expect(within(menu).getByRole('menuitem', { name: 'Test connection', hidden: true })).toBeInTheDocument();
    expect(within(menu).getByRole('menuitem', { name: 'Pause connection', hidden: true })).toBeInTheDocument();
    expect(within(menu).getByRole('menuitem', { name: 'View history', hidden: true })).toBeInTheDocument();
  });

  it('tests a connection and shows a 401 result on the card', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/v1/platforms/:connectionId/test', () =>
        HttpResponse.json({
          success: true,
          data: {
            connectionId: platforms[0].id,
            outcome: 'AUTHENTICATION_FAILED',
            httpStatus: 401,
            message:
              'The channel rejected the credentials (HTTP 401). Check the token and save it again.',
            testedAt: '2026-10-28T14:05:00Z',
            lastInboundOrderAt: null,
          },
          meta: { timestamp: '2026-10-28T14:05:00Z' },
        }),
      ),
    );

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'Test connection');

    expect(
      await screen.findByText(
        'The channel rejected the credentials (HTTP 401). Check the token and save it again.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Authentication failed')).toBeInTheDocument();
    expect(await screen.findByText('Connection test failed')).toBeInTheDocument();
  });

  it('shows awaiting first inbound order for a webhook-only channel', async () => {
    const user = userEvent.setup();
    const webhookOnly: PlatformConnection = {
      ...platforms[0],
      id: '00000000-0000-4000-8000-000000000899',
      platformType: 'CUSTOM_WEBHOOK',
      displayName: 'Studio Craft Fair',
      slug: 'studio-craft-fair',
      apiEndpoint: 'https://your-automation.example.com/stockledger/orders',
      webhookPath: '/api/v1/webhooks/orders/studio-craft-fair',
      syncStatus: 'PENDING',
      syncSupported: false,
      lastSyncAt: null,
      lastErrorMessage: null,
      lastTestedAt: null,
      lastTestOutcome: null,
      lastTestHttpStatus: null,
      lastTestDetail: null,
      lastInboundOrderAt: null,
    };
    server.use(
      http.get('/api/v1/platforms', () =>
        HttpResponse.json({
          success: true,
          data: [webhookOnly],
          meta: { timestamp: '2026-10-28T14:10:00Z' },
        }),
      ),
      http.post('/api/v1/platforms/:connectionId/test', () =>
        HttpResponse.json({
          success: true,
          data: {
            connectionId: webhookOnly.id,
            outcome: 'AWAITING_INBOUND',
            httpStatus: null,
            message:
              'This channel only receives orders. No orders have arrived yet, so there is nothing to call.',
            testedAt: null,
            lastInboundOrderAt: null,
          },
          meta: { timestamp: '2026-10-28T14:10:00Z' },
        }),
      ),
    );

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Custom Channel' });
    expect(screen.getByText('Awaiting first inbound order.')).toBeInTheDocument();

    chooseMenuItem(await openActionsMenu(user, 'Custom Channel'), 'Test connection');

    expect(
      await screen.findByText(
        'This channel only receives orders. No orders have arrived yet, so there is nothing to call.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Awaiting first inbound order')).toBeInTheDocument();
  });

  it('edits a connection without resending a secret that was left blank', async () => {
    const user = userEvent.setup();
    let receivedBody: Record<string, unknown> | null = null;
    server.use(
      http.patch('/api/v1/platforms/:connectionId', async ({ request }) => {
        receivedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          success: true,
          data: {
            ...platforms[0],
            displayName: 'Northwind Supply EU',
            version: platforms[0].version + 1,
          },
          meta: { timestamp: '2026-10-28T11:00:00Z' },
        });
      }),
    );

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'Edit connection');

    const dialog = await screen.findByRole('dialog', { name: 'Edit Shopify' });
    const webhookAddress = within(dialog).getByLabelText('Webhook address');
    expect(webhookAddress).toHaveValue('/api/v1/webhooks/orders/shopify');
    expect(webhookAddress).toHaveAttribute('readonly');
    expect(within(dialog).getByLabelText('Webhook secret')).toHaveValue('');
    expect(within(dialog).getByLabelText('Access token')).toHaveValue('');

    const displayName = within(dialog).getByLabelText(/^Display name/);
    await user.clear(displayName);
    await user.type(displayName, 'Northwind Supply EU');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(receivedBody).toEqual({
        version: platforms[0].version,
        displayName: 'Northwind Supply EU',
        apiEndpoint: platforms[0].apiEndpoint,
      }),
    );
    expect(await screen.findByText('Northwind Supply EU')).toBeInTheDocument();
  });

  it('requires a reason to pause and keeps that reason on the card', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'Pause connection');

    const dialog = await screen.findByRole('dialog', { name: 'Pause Shopify' });
    await user.click(within(dialog).getByRole('button', { name: 'Pause connection' }));

    expect(
      await within(dialog).findByText(
        'Give a reason for pausing this connection. It is recorded in the connection history.',
      ),
    ).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/^Reason/), PAUSE_REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Pause connection' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByText(PAUSE_REASON)).toBeInTheDocument();
    expect(screen.getByText('Paused')).toBeInTheDocument();
  });

  it('surfaces an address conflict when resuming a paused connection', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('/api/v1/platforms', () =>
        HttpResponse.json({
          success: true,
          data: [pausedShopify],
          meta: { timestamp: '2026-10-28T11:05:00Z' },
        }),
      ),
      http.patch('/api/v1/platforms/:connectionId', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/platform-webhook-address-in-use',
            title: 'Platform Webhook Address In Use',
            status: 409,
            detail:
              'Another active connection already receives orders at /api/v1/webhooks/orders/shopify. Pause that connection first.',
            code: 'PLATFORM_WEBHOOK_ADDRESS_IN_USE',
            timestamp: '2026-10-28T11:05:00Z',
            invalidParams: [],
          },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'Resume connection');

    const dialog = await screen.findByRole('dialog', { name: 'Resume Shopify' });
    await user.click(within(dialog).getByRole('button', { name: 'Resume connection' }));

    expect(
      await screen.findByText(
        'Another active connection already receives orders at /api/v1/webhooks/orders/shopify. Pause that connection first.',
      ),
    ).toBeInTheDocument();
  });

  it('shows who paused a connection, why, and which fields an edit changed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'View history');

    const dialog = await screen.findByRole('dialog', { name: 'History for Shopify' });

    expect(
      await within(dialog).findByText(`Paused because: ${PAUSE_REASON}`),
    ).toBeInTheDocument();
    expect(within(dialog).getByText('Changed API address and webhook secret.')).toBeInTheDocument();
    expect(within(dialog).getAllByText(/mara\.ellison@northwind-supply\.co/).length).toBeGreaterThan(0);
    expect(within(dialog).getByText('Connection created')).toBeInTheDocument();
  });

  it('loads older history entries on request', async () => {
    const user = userEvent.setup();
    const connection = platforms[0];
    server.use(
      http.get('/api/v1/platforms/:connectionId/events', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        const event =
          page === 0
            ? {
                id: '00000000-0000-4000-8000-000000000861',
                platformConnectionId: connection.id,
                platformType: 'SHOPIFY',
                changeType: 'RESUMED',
                actorUserId: '00000000-0000-4000-8000-000000000001',
                actorEmail: 'mara.ellison@northwind-supply.co',
                fields: ['active'],
                reason: null,
                occurredAt: '2026-10-24T08:15:00Z',
              }
            : {
                id: '00000000-0000-4000-8000-000000000862',
                platformConnectionId: connection.id,
                platformType: 'SHOPIFY',
                changeType: 'CREATED',
                actorUserId: '00000000-0000-4000-8000-000000000001',
                actorEmail: 'mara.ellison@northwind-supply.co',
                fields: [],
                reason: null,
                occurredAt: '2026-07-01T09:00:00Z',
              };
        return HttpResponse.json({
          success: true,
          data: [event],
          pagination: {
            page,
            size: 1,
            totalElements: 2,
            totalPages: 2,
            hasMore: page === 0,
          },
          meta: { timestamp: '2026-10-28T11:10:00Z' },
        });
      }),
    );

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });
    await screen.findByRole('heading', { name: 'Shopify' });
    chooseMenuItem(await openActionsMenu(user, 'Shopify'), 'View history');

    const dialog = await screen.findByRole('dialog', { name: 'History for Shopify' });
    expect(await within(dialog).findByText('Started receiving orders again.')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Show older changes' }));

    expect(await within(dialog).findByText('This connection was set up.')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Show older changes' })).not.toBeInTheDocument();
  });

  it('names the channels that can be connected from the server catalogue', async () => {
    server.use(http.get('/api/v1/platforms', () => HttpResponse.json({ success: true, data: [] })));

    renderWithProviders(<PlatformsPage />, { route: '/settings/platforms' });

    expect(await screen.findByText('No platform connections')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Connect Shopify, WooCommerce, TikTok Shop, Amazon, Square POS and Custom Channel to import orders automatically.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Etsy/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Connect your first platform/ })).toHaveAttribute(
      'href',
      '/settings/platforms/new',
    );
  });
});
