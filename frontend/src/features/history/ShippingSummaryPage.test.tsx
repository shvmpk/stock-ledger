import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ShippingSummaryPage } from './ShippingSummaryPage';
import { renderWithProviders } from '../../test/render';

describe('ShippingSummaryPage', () => {
  it('shows monthly totals and marks a negative margin as a loss', async () => {
    renderWithProviders(<ShippingSummaryPage />, { route: '/history/shipping' });

    expect(await screen.findByRole('heading', { name: 'Shipping summary' })).toBeInTheDocument();
    await screen.findByRole('table');
    const totals = screen.getByLabelText('Shipping summary totals');
    expect(totals).toHaveTextContent('58');
    expect(totals).toHaveTextContent('£352.20');
    expect(totals).toHaveTextContent('£345.95');
    expect(totals).toHaveTextContent('£6.25');

    const table = screen.getByRole('table');
    expect(within(table).getByText('October 2026')).toBeInTheDocument();
    expect(within(table).getByText('September 2026')).toBeInTheDocument();
    expect(within(table).getByText('August 2026')).toBeInTheDocument();
    expect(within(table).getByText('-£22.70')).toBeInTheDocument();
    expect(within(table).getByText('Loss')).toBeInTheDocument();
  });

  it('filters to a single month and recomputes the totals', async () => {
    renderWithProviders(<ShippingSummaryPage />, { route: '/history/shipping' });
    await screen.findByRole('table');

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2026-09' } });

    const totals = screen.getByLabelText('Shipping summary totals');
    expect(totals).toHaveTextContent('24');
    expect(totals).toHaveTextContent('£148.60');
    expect(totals).toHaveTextContent('£171.30');
    expect(totals).toHaveTextContent('-£22.70');
    expect(screen.queryByText('October 2026')).not.toBeInTheDocument();
    expect(screen.getByText('September 2026')).toBeInTheDocument();
  });

  it('shows an empty state for a month without shipments and restores all months', async () => {
    renderWithProviders(<ShippingSummaryPage />, { route: '/history/shipping' });
    await screen.findByRole('table');

    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2026-07' } });

    expect(await screen.findByText('No shipments in July 2026')).toBeInTheDocument();
    expect(screen.getAllByText('Show all months').length).toBeGreaterThan(0);
    fireEvent.click(screen.getAllByRole('button', { name: 'Show all months' })[0]);

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByText('August 2026')).toBeInTheDocument();
  });
});
