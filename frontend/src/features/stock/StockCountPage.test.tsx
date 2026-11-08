import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { StockCountPage } from './StockCountPage';
import { completedCount } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

describe('StockCountPage', () => {
  it('resumes the active session, records a variance, and surfaces a reservation deficit', async () => {
    const user = userEvent.setup();
    renderWithProviders(<StockCountPage />);

    expect(await screen.findByText('In progress')).toBeInTheDocument();
    expect(await screen.findByText(/No counts recorded yet/)).toBeInTheDocument();

    const counted = screen.getAllByLabelText('Counted on hand for HOOD-NVY-XL')[0];
    await user.clear(counted);
    await user.type(counted, '1');

    await user.click(screen.getByRole('button', { name: 'Review counts' }));
    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText(/Total variance/)).toBeInTheDocument();

    await user.click(within(drawer).getByRole('button', { name: 'Record & complete' }));

    expect(await screen.findByText('Physical count is below reserved stock')).toBeInTheDocument();
    expect(screen.getByText(/below reserved stock by 1 units/)).toBeInTheDocument();
    expect(screen.getByText(/1 variants recorded/)).toBeInTheDocument();
  });

  it('shows variant avatars and category icons in the count workspace', async () => {
    const user = userEvent.setup();
    renderWithProviders(<StockCountPage />);

    expect(await screen.findByText('In progress')).toBeInTheDocument();
    const skus = screen
      .getAllByTestId('variant-avatar')
      .map((avatar) => avatar.getAttribute('data-sku'));
    expect(skus).toEqual(
      expect.arrayContaining(['HOOD-NVY-XL', 'HOOD-NVY-M', 'COF-ETH-250G']),
    );

    await user.click(screen.getByLabelText('Category', { selector: 'input' }));
    const option = await screen.findByRole('option', { name: 'Homeware', hidden: true });
    expect(within(option).getByTestId('category-icon')).toHaveAttribute(
      'data-category',
      'Homeware',
    );
  });

  it('resumes a completed session from a deep link and shows its recorded variances', async () => {
    renderWithProviders(<StockCountPage />, {
      route: `/stock/count?session=${completedCount.id}`,
    });

    expect(await screen.findByText('Stock count completed')).toBeInTheDocument();
    expect(screen.getByText(/1 variants recorded/)).toBeInTheDocument();
    expect(screen.getAllByText('Stoneware Pour-Over Set').length).toBeGreaterThan(0);
    expect(screen.getAllByText('POUR-SET-SND').length).toBeGreaterThan(0);
    expect(screen.getAllByText('−1').length).toBeGreaterThan(0);
  });

  it('auto-creates a session when no count is in progress', async () => {
    server.use(
      http.get('/api/v1/stock/counts', () =>
        HttpResponse.json({
          success: true,
          data: [],
          pagination: { page: 0, size: 10, totalElements: 0, totalPages: 0, hasMore: false },
          meta: { timestamp: '2026-10-23T09:00:00Z' },
        }),
      ),
    );

    renderWithProviders(<StockCountPage />);

    expect(await screen.findByText('In progress')).toBeInTheDocument();
    expect(await screen.findByText(/No counts recorded yet/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Review counts' })).toBeInTheDocument();
  });
});
