import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  apiGet,
  apiGetPage,
  apiRequest,
  downloadCsv,
  parseFilename,
} from './client';
import { ApiError, UNAUTHORIZED_EVENT } from './errors';
import {
  resetPasswordChangeRedirectGuard,
  setPasswordChangeRedirectHandler,
} from './passwordChangeRedirect';
import { server } from '../../test/server';

afterEach(() => {
  setPasswordChangeRedirectHandler(null);
  resetPasswordChangeRedirectGuard();
});

describe('API client', () => {
  it('unwraps the response envelope and pagination metadata', async () => {
    server.use(
      http.get('/api/v1/orders', () =>
        HttpResponse.json({
          success: true,
          data: [{ id: 'order-1' }],
          pagination: {
            page: 2,
            size: 20,
            totalElements: 41,
            totalPages: 3,
            hasMore: true,
          },
          meta: { timestamp: '2026-10-23T09:00:00Z' },
        }),
      ),
    );

    const result = await apiGetPage<Array<{ id: string }>>('/orders', {
      page: 2,
      size: 20,
      status: 'PENDING',
    });

    expect(result.data).toEqual([{ id: 'order-1' }]);
    expect(result.pagination).toMatchObject({
      page: 2,
      size: 20,
      totalElements: 41,
      hasMore: true,
    });
  });

  it('maps RFC 7807 problem details onto ApiError', async () => {
    server.use(
      http.post('/api/v1/orders', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/insufficient-stock',
            title: 'Insufficient Stock',
            status: 409,
            detail: 'Cannot reserve 5 units of HOOD-NVY-XL. Only 2 units available.',
            code: 'INSUFFICIENT_STOCK',
            timestamp: '2026-10-23T09:00:00Z',
            invalidParams: [{ field: 'items[0].quantity', message: 'Not enough stock.' }],
          },
          { status: 409 },
        ),
      ),
    );

    const error = await apiRequest('/orders', { method: 'POST', body: {} }).catch(
      (caught: unknown) => caught,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 409,
      code: 'INSUFFICIENT_STOCK',
      title: 'Insufficient Stock',
      detail: 'Cannot reserve 5 units of HOOD-NVY-XL. Only 2 units available.',
    });
    expect((error as ApiError).fieldError('items[0].quantity')).toBe('Not enough stock.');
  });

  it('broadcasts unauthorized events for operational requests', async () => {
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    server.use(
      http.get('/api/v1/products', () =>
        HttpResponse.json(
          { title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
          { status: 401 },
        ),
      ),
    );

    await expect(apiGet('/products')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });

  it('does not broadcast unauthorized for the session bootstrap request', async () => {
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json(
          { title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
          { status: 401 },
        ),
      ),
    );

    await expect(apiGet('/auth/me')).rejects.toBeInstanceOf(ApiError);
    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
  });

  it('redirects once when concurrent requests are blocked by PASSWORD_CHANGE_REQUIRED', async () => {
    const redirect = vi.fn();
    setPasswordChangeRedirectHandler(redirect);
    const unauthorized = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, unauthorized);

    const blocked = () =>
      HttpResponse.json(
        {
          type: 'https://api.stockledger.io/errors/password-change-required',
          title: 'Password Change Required',
          status: 403,
          detail: 'Your password needs to be updated before you can continue.',
          code: 'PASSWORD_CHANGE_REQUIRED',
          timestamp: '2026-10-26T12:00:00Z',
          invalidParams: [],
        },
        { status: 403 },
      );
    server.use(
      http.get('/api/v1/products', () => blocked()),
      http.get('/api/v1/orders', () => blocked()),
    );

    const results = await Promise.allSettled([apiGet('/products'), apiGet('/orders')]);

    expect(results.every((result) => result.status === 'rejected')).toBe(true);
    for (const result of results) {
      if (result.status === 'rejected') {
        expect(result.reason).toBeInstanceOf(ApiError);
        expect(result.reason).toMatchObject({
          status: 403,
          code: 'PASSWORD_CHANGE_REQUIRED',
          detail: 'Your password needs to be updated before you can continue.',
        });
      }
    }
    expect(redirect).toHaveBeenCalledTimes(1);
    expect(unauthorized).not.toHaveBeenCalled();
    window.removeEventListener(UNAUTHORIZED_EVENT, unauthorized);
  });

  it('turns a failed fetch into a network ApiError', async () => {
    server.use(http.get('/api/v1/products', () => HttpResponse.error()));

    const error = await apiGet('/products').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
      detail: 'Cannot reach StockLedger. Check the API and try again.',
    });
  });

  it('parses RFC 5987 and plain Content-Disposition filenames', () => {
    expect(parseFilename("attachment; filename*=UTF-8''stock%20history.csv", 'fallback.csv')).toBe(
      'stock history.csv',
    );
    expect(parseFilename('attachment; filename="catalog_export.csv"', 'fallback.csv')).toBe(
      'catalog_export.csv',
    );
    expect(parseFilename(null, 'fallback.csv')).toBe('fallback.csv');
  });

  it('downloads a CSV through a temporary anchor and returns the filename', async () => {
    server.use(
      http.get('/api/v1/export/catalog', () =>
        new HttpResponse('sku,product\nHOOD-NVY-XL,Heavyweight Cotton Hoodie\n', {
          headers: {
            'Content-Type': 'text/csv',
            'Content-Disposition': 'attachment; filename="catalog_export.csv"',
          },
        }),
      ),
    );
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:catalog');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    const filename = await downloadCsv('/export/catalog', 'fallback.csv');

    expect(filename).toBe('catalog_export.csv');
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith('blob:catalog'));
  });
});
