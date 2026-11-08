import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { SecurityLogPage } from './SecurityLogPage';
import { authEvents } from '../../test/fixtures';
import { formatDateTime } from '../../lib/format/date';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import type { AuthEvent } from '../../lib/api/types';

function authEventPage(data: AuthEvent[], page = 0, size = 20) {
  return HttpResponse.json({
    success: true,
    data,
    pagination: {
      page,
      size,
      totalElements: data.length,
      totalPages: Math.max(1, Math.ceil(data.length / size)),
      hasMore: page === 0 && data.length > size,
    },
    meta: { timestamp: new Date().toISOString() },
  });
}

async function chooseOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(screen.getByLabelText(label, { selector: 'input' }));
  await user.click(await screen.findByRole('option', { name: option, hidden: true }));
}

describe('SecurityLogPage', () => {
  it('renders signed-in and password-reset events as human sentences, never raw enum values', async () => {
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });

    expect(await screen.findByRole('heading', { name: 'Security log' })).toBeInTheDocument();
    expect((await screen.findAllByText('Mara Ellison signed in')).length).toBeGreaterThan(0);
    expect(
      (await screen.findAllByText("Mara Ellison reset Theo Marsh's password")).length,
    ).toBeGreaterThan(0);

    expect(screen.queryAllByText('LOGIN_SUCCEEDED')).toHaveLength(0);
    expect(screen.queryAllByText('PASSWORD_RESET')).toHaveLength(0);
    expect(screen.queryAllByText(/fromRole/)).toHaveLength(0);
    expect(screen.queryAllByText(/\{"fromRole"/)).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /export|CSV/i })).not.toBeInTheDocument();
  });

  it('shows sign-ins and security events by default and reveals logouts only under All activity', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });

    await screen.findAllByText('Mara Ellison signed in');
    expect(screen.queryAllByText('Mara Ellison signed out')).toHaveLength(0);

    await chooseOption(user, 'Activity', 'All activity');

    expect((await screen.findAllByText('Mara Ellison signed out')).length).toBeGreaterThan(0);
  });

  it('shows relative times with the exact local timestamp as a tooltip', async () => {
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });

    const event = authEvents[0];
    const labels = await screen.findAllByText('12 minutes ago');
    const time = labels[0].closest('time');

    expect(time).toHaveAttribute('dateTime', event.occurredAt);
    expect(time).toHaveAttribute('title', formatDateTime(event.occurredAt));
  });

  it('filters by user email and returns only that account activity', async () => {
    const requests: URL[] = [];
    server.use(
      http.get('/api/v1/admin/auth-events', ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const actorEmail = url.searchParams.get('actorEmail') ?? '';
        const filtered = authEvents.filter((event) =>
          (event.actorEmail ?? '').toLowerCase().includes(actorEmail.toLowerCase()),
        );
        return authEventPage(filtered);
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });
    await screen.findAllByText('Mara Ellison signed in');

    await user.type(screen.getByLabelText('User'), 'staff');

    expect(
      (await screen.findAllByText('Theo Marsh changed their password')).length,
    ).toBeGreaterThan(0);
    expect(screen.queryAllByText('Mara Ellison signed in')).toHaveLength(0);
    await waitFor(() => {
      expect(requests.at(-1)?.searchParams.get('actorEmail')).toBe('staff');
    });
  });

  it('applies date presets as from/to filters and resets to the first page', async () => {
    const requests: URL[] = [];
    const manyEvents = Array.from({ length: 45 }, (_, index) => ({
      ...authEvents[index % authEvents.length],
      id: `00000000-0000-4000-8000-${String(700000 + index).padStart(12, '0')}`,
      occurredAt: new Date(Date.now() - index * 60_000).toISOString(),
    }));

    server.use(
      http.get('/api/v1/admin/auth-events', ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const page = Number(url.searchParams.get('page') ?? 0);
        const size = Number(url.searchParams.get('size') ?? 20);
        const start = page * size;
        return HttpResponse.json({
          success: true,
          data: manyEvents.slice(start, start + size),
          pagination: {
            page,
            size,
            totalElements: manyEvents.length,
            totalPages: Math.ceil(manyEvents.length / size),
            hasMore: start + size < manyEvents.length,
          },
          meta: { timestamp: new Date().toISOString() },
        });
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });
    await screen.findAllByText('Mara Ellison signed in');

    await user.click(screen.getByRole('button', { name: '2' }));
    await waitFor(() => {
      expect(requests.at(-1)?.searchParams.get('page')).toBe('1');
    });

    await chooseOption(user, 'Time range', 'Last 7 days');

    await waitFor(() => {
      const latest = requests.at(-1);
      expect(latest?.searchParams.get('page')).toBe('0');
      expect(latest?.searchParams.get('from')).toBeTruthy();
      expect(latest?.searchParams.get('to')).toBeTruthy();
    });
    const latest = requests.at(-1)!;
    const spanMs =
      new Date(latest.searchParams.get('to')!).getTime() -
      new Date(latest.searchParams.get('from')!).getTime();
    expect(spanMs).toBeGreaterThan(6.9 * 24 * 60 * 60 * 1000);
    expect(spanMs).toBeLessThan(7.1 * 24 * 60 * 60 * 1000);
  });

  it('offers a mobile card layout for the same events', async () => {
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });
    await screen.findAllByText('Mara Ellison signed in');

    const cards = screen.getByLabelText('Security events, newest first.');
    expect(within(cards).getByText('Mara Ellison signed in')).toBeInTheDocument();
    expect(within(cards).getByText('This device')).toBeInTheDocument();
  });

  it('renders an empty state when no events match', async () => {
    server.use(http.get('/api/v1/admin/auth-events', () => authEventPage([])));

    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });

    expect(await screen.findByText('No security events')).toBeInTheDocument();
  });

  it('renders an error state with a working retry', async () => {
    let attempts = 0;
    server.use(
      http.get('/api/v1/admin/auth-events', () => {
        attempts += 1;
        if (attempts === 1) {
          return HttpResponse.json(
            {
              type: 'https://api.stockledger.io/errors/internal-error',
              title: 'Internal Server Error',
              status: 500,
              detail: 'The security log could not be read.',
              code: 'INTERNAL_ERROR',
              timestamp: new Date().toISOString(),
              invalidParams: [],
            },
            { status: 500 },
          );
        }
        return authEventPage(authEvents);
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<SecurityLogPage />, { route: '/settings/security-log' });

    expect(await screen.findByText('The security log could not be read.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect((await screen.findAllByText('Mara Ellison signed in')).length).toBeGreaterThan(0);
    expect(attempts).toBe(2);
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<SecurityLogPage />, {
      route: '/settings/security-log',
    });

    expect((await screen.findAllByText('Mara Ellison signed in')).length).toBeGreaterThan(0);
    expect(await axe(container)).toHaveNoViolations();
  });
});
