import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ExportsPage } from './ExportsPage';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

describe('ExportsPage', () => {
  it('downloads each CSV and shows the parsed Content-Disposition filename', async () => {
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    const user = userEvent.setup();
    renderWithProviders(<ExportsPage />, { route: '/settings/exports' });

    expect(await screen.findByRole('heading', { name: 'Exports' })).toBeInTheDocument();
    expect(screen.getByText('Owner access · contains financial data')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Download catalog' }));
    expect(await screen.findByText('Export ready')).toBeInTheDocument();
    expect(await screen.findByText('catalog_export.csv downloaded.')).toBeInTheDocument();
    expect(screen.getByText(/Last download:/)).toHaveTextContent('catalog_export.csv');
    expect(anchorClick).toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Download orders' }));
    expect(await screen.findByText('orders_export.csv downloaded.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText(/Last download:/)).toHaveTextContent('orders_export.csv'),
    );

    await user.click(screen.getByRole('button', { name: 'Download stock history' }));
    expect(await screen.findByText('stock_history_export.csv downloaded.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText(/Last download:/)).toHaveTextContent('stock_history_export.csv'),
    );

    anchorClick.mockRestore();
  });

  it('shows the structured API error when an export is forbidden', async () => {
    server.use(
      http.get('/api/v1/export/catalog', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/forbidden',
            title: 'Forbidden',
            status: 403,
            detail: 'Owner access is required to export catalog data.',
            code: 'ACCESS_DENIED',
            timestamp: '2026-10-23T09:00:00Z',
            invalidParams: [],
          },
          { status: 403 },
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ExportsPage />, { route: '/settings/exports' });
    await screen.findByRole('heading', { name: 'Exports' });

    await user.click(screen.getByRole('button', { name: 'Download catalog' }));

    expect(await screen.findByText('Export failed')).toBeInTheDocument();
    expect(
      screen.getByText('Owner access is required to export catalog data.'),
    ).toBeInTheDocument();
    expect(screen.getByText('ACCESS_DENIED')).toBeInTheDocument();
    expect(screen.queryByText(/Last download:/)).not.toBeInTheDocument();
  });
});
