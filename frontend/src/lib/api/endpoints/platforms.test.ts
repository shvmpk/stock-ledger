import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  createPlatform,
  listPlatformEvents,
  newIdempotencyKey,
  updatePlatform,
} from './platforms';
import { platforms } from '../../../test/fixtures';
import { server } from '../../../test/server';

const SHOPIFY_ENDPOINT = 'https://northwind-supply.myshopify.com/admin/api/2026-10';
const SHOPIFY_WEBHOOK_SECRET = 'dummy-webhook-secret-2';
const SHOPIFY_ACCESS_TOKEN = 'dummy-shopify-token-2';

describe('platform endpoints', () => {
  it('creates a connection with the idempotency key so a retry cannot duplicate it', async () => {
    const idempotencyKey = '6f2a9c4e-1b7d-4e83-9a05-3c8d2f6b1e47';
    let receivedKey: string | null = null;
    let receivedBody: Record<string, unknown> | null = null;

    server.use(
      http.post('/api/v1/platforms', async ({ request }) => {
        receivedKey = request.headers.get('Idempotency-Key');
        receivedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          {
            success: true,
            data: {
              ...platforms[0],
              id: '00000000-0000-4000-8000-000000000901',
              displayName: 'Northwind Supply',
              apiEndpoint: SHOPIFY_ENDPOINT,
              version: 0,
            },
            meta: { timestamp: '2026-10-28T10:00:00Z' },
          },
          { status: 201 },
        );
      }),
    );

    const created = await createPlatform(
      {
        platformType: 'SHOPIFY',
        displayName: 'Northwind Supply',
        apiEndpoint: SHOPIFY_ENDPOINT,
        webhookSecret: SHOPIFY_WEBHOOK_SECRET,
        accessToken: SHOPIFY_ACCESS_TOKEN,
      },
      idempotencyKey,
    );

    expect(receivedKey).toBe(idempotencyKey);
    expect(receivedBody).toMatchObject({
      platformType: 'SHOPIFY',
      displayName: 'Northwind Supply',
      apiEndpoint: SHOPIFY_ENDPOINT,
      webhookSecret: SHOPIFY_WEBHOOK_SECRET,
      accessToken: SHOPIFY_ACCESS_TOKEN,
    });
    expect(created.id).toBe('00000000-0000-4000-8000-000000000901');
    expect(created.version).toBe(0);
  });

  it('sends the version read by the owner when saving an edit', async () => {
    const connection = platforms[0];
    let receivedBody: Record<string, unknown> | null = null;

    server.use(
      http.patch('/api/v1/platforms/:connectionId', async ({ params, request }) => {
        expect(params.connectionId).toBe(connection.id);
        receivedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          success: true,
          data: {
            ...connection,
            displayName: 'Northwind Supply EU',
            version: connection.version + 1,
          },
          meta: { timestamp: '2026-10-28T10:05:00Z' },
        });
      }),
    );

    const updated = await updatePlatform(connection.id, {
      version: connection.version,
      displayName: 'Northwind Supply EU',
    });

    expect(receivedBody).toEqual({
      version: connection.version,
      displayName: 'Northwind Supply EU',
    });
    expect(updated.displayName).toBe('Northwind Supply EU');
    expect(updated.version).toBe(connection.version + 1);
  });

  it('reads one page of a connection history with the page number and size', async () => {
    const connection = platforms[0];
    let receivedQuery: { page: string | null; size: string | null } | null = null;

    server.use(
      http.get('/api/v1/platforms/:connectionId/events', ({ params, request }) => {
        expect(params.connectionId).toBe(connection.id);
        const url = new URL(request.url);
        receivedQuery = {
          page: url.searchParams.get('page'),
          size: url.searchParams.get('size'),
        };
        return HttpResponse.json({
          success: true,
          data: [
            {
              id: '00000000-0000-4000-8000-000000000851',
              platformConnectionId: connection.id,
              platformType: 'SHOPIFY',
              changeType: 'PAUSED',
              actorUserId: '00000000-0000-4000-8000-000000000001',
              actorEmail: 'mara.ellison@northwind-supply.co',
              fields: ['active'],
              reason: 'Store closed for a seasonal break.',
              occurredAt: '2026-10-23T17:40:00Z',
            },
          ],
          pagination: {
            page: 1,
            size: 10,
            totalElements: 11,
            totalPages: 2,
            hasMore: false,
          },
          meta: { timestamp: '2026-10-28T10:10:00Z' },
        });
      }),
    );

    const result = await listPlatformEvents(connection.id, 1, 10);

    expect(receivedQuery).toEqual({ page: '1', size: '10' });
    expect(result.data).toHaveLength(1);
    expect(result.data[0].reason).toBe('Store closed for a seasonal break.');
    expect(result.pagination?.hasMore).toBe(false);
    expect(result.pagination?.totalElements).toBe(11);
  });

  it('makes URL-safe request keys that differ on every call', () => {
    const first = newIdempotencyKey();
    const second = newIdempotencyKey();

    expect(first).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(second).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(first).not.toBe(second);
  });
});
