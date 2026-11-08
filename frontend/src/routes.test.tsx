import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, useLocation, useRoutes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { routeObjects } from './routes';
import { theme } from './theme/theme';
import { staffUser } from './test/fixtures';
import { createTestQueryClient } from './test/render';
import { server } from './test/server';

function RoutesRenderer() {
  return useRoutes(routeObjects);
}

function LocationDisplay() {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function renderRoute(initialEntry: string) {
  const queryClient = createTestQueryClient();
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MantineProvider theme={theme} defaultColorScheme="light">
        <Notifications position="top-right" />
        <MemoryRouter initialEntries={[initialEntry]}>
          <LocationDisplay />
          <RoutesRenderer />
        </MemoryRouter>
      </MantineProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

function currentLocation() {
  return screen.getByTestId('location').textContent ?? '';
}

function unauthenticatedMe() {
  return HttpResponse.json(
    {
      type: 'https://api.stockledger.io/errors/unauthorized',
      title: 'Unauthorized',
      status: 401,
      detail: 'Authentication is required.',
      code: 'UNAUTHORIZED',
      timestamp: '2026-10-23T09:00:00Z',
      invalidParams: [],
    },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

describe('application routes', () => {
  it('redirects an anonymous visitor to login with the original destination', async () => {
    server.use(http.get('/api/v1/auth/me', () => unauthenticatedMe()));
    renderRoute('/orders/new?status=PENDING');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(new URL(currentLocation(), 'http://localhost').pathname).toBe('/login');
    expect(new URLSearchParams(currentLocation().split('?')[1] ?? '').get('next')).toBe(
      '/orders/new?status=PENDING',
    );
  });

  it('blocks staff from owner-only settings routes', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/settings/users');

    expect(await screen.findByRole('heading', { name: 'Owner access required' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/settings/users');
    expect(screen.queryByRole('heading', { name: 'Users' })).not.toBeInTheDocument();
  });

  it('lets staff use operational order routes', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/orders/new');

    expect(await screen.findByText('New orders')).toBeInTheDocument();
    expect((await screen.findAllByText('ORD-2026-0112')).length).toBeGreaterThan(0);
  });

  it('lets staff reach the voluntary password-change screen without the app shell', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/account/password');

    expect(
      await screen.findByRole('heading', { name: 'Change password' }),
    ).toBeInTheDocument();
    expect(currentLocation()).toBe('/account/password');
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('shows the security log to owners', async () => {
    renderRoute('/settings/security-log');

    expect(await screen.findByRole('heading', { name: 'Security log' })).toBeInTheDocument();
    expect((await screen.findAllByText('Mara Ellison signed in')).length).toBeGreaterThan(0);
  });

  it('blocks staff from the security log', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/settings/security-log');

    expect(
      await screen.findByRole('heading', { name: 'Owner access required' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Security log' })).not.toBeInTheDocument();
  });

  it('lets an owner open the add-platform screen', async () => {
    renderRoute('/settings/platforms/new');

    expect(
      await screen.findByRole('heading', { name: 'Connect a platform' }),
    ).toBeInTheDocument();
    expect(currentLocation()).toBe('/settings/platforms/new');
  });

  it('blocks staff from adding a platform', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/settings/platforms/new');

    expect(
      await screen.findByRole('heading', { name: 'Owner access required' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Connect a platform' })).not.toBeInTheDocument();
  });

  it('lets an owner open user administration', async () => {
    renderRoute('/settings/users');

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /Mara Ellison/ })).toBeInTheDocument();
    expect(currentLocation()).toBe('/settings/users');
  });

  it('redirects the root route to the stock catalog', async () => {
    renderRoute('/');

    expect(await screen.findByRole('heading', { name: 'Stock' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/stock/products');
  });

  it('redirects the retired low-stock address to the reorder list', async () => {
    renderRoute('/stock/low-stock');

    expect(await screen.findByRole('heading', { name: 'Reorder' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/stock/reorder');
  });

  it('lets staff open the reorder list and read the buying assumptions', async () => {
    server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ success: true, data: staffUser })));
    renderRoute('/stock/reorder');

    expect(await screen.findByRole('heading', { name: 'Reorder' })).toBeInTheDocument();
    expect((await screen.findAllByText('HOOD-NVY-M')).length).toBeGreaterThan(0);
    expect(screen.getByText(/14-day supplier lead time/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit lead time' })).not.toBeInTheDocument();
  });

  it('redirects /orders to the new-order queue', async () => {
    renderRoute('/orders');

    expect(await screen.findByText('New orders')).toBeInTheDocument();
    expect(currentLocation()).toBe('/orders/new');
  });

  it('renders the not-found page for unknown routes', async () => {
    renderRoute('/supplier-invoices/2026-10');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(currentLocation()).toBe('/supplier-invoices/2026-10');
  });
});
