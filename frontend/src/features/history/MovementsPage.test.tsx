import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { MovementsPage } from './MovementsPage';
import { completedCount, pendingOrder } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';

describe('MovementsPage', () => {
  it('renders the append-only ledger summary and rows', async () => {
    renderWithProviders(<MovementsPage />, { route: '/history/movements' });

    expect(await screen.findByRole('heading', { name: 'Stock movements' })).toBeInTheDocument();
    expect(screen.getByLabelText('Movement page summary')).toHaveTextContent('Matching movements');
    expect((await screen.findAllByText('Heavyweight Cotton Hoodie')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Stoneware Pour-Over Set').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Receive').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Audit correction').length).toBeGreaterThan(0);
  });

  it('filters the ledger by SKU and clears the filter', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MovementsPage />, { route: '/history/movements' });
    await screen.findAllByText('Stoneware Pour-Over Set');

    await user.type(screen.getByLabelText('Filter by SKU'), 'HOOD-NVY-XL');

    await waitFor(() =>
      expect(screen.queryAllByText('Stoneware Pour-Over Set')).toHaveLength(0),
    );
    expect(screen.getAllByText('Heavyweight Cotton Hoodie').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(await screen.findAllByText('Stoneware Pour-Over Set')).not.toHaveLength(0);
  });

  it('filters by reason and validates an inverted date range', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MovementsPage />, { route: '/history/movements' });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    await user.click(screen.getAllByLabelText('Reason')[0]);
    const option = (await screen.findAllByText('Order reserved')).find((element) =>
      element.closest('[role="option"]'),
    );
    await user.click(option as HTMLElement);
    const table = screen.getByRole('table');
    await waitFor(() =>
      expect(within(table).queryAllByText('Audit correction')).toHaveLength(0),
    );
    expect(within(table).getAllByText('Order reserved').length).toBeGreaterThan(0);

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-10-22' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-10-21' } });

    expect(
      await screen.findByText('The end date must be on or after the start date.'),
    ).toBeInTheDocument();
  });

  it('summarises the filtered SKU above the ledger rows', async () => {
    renderWithProviders(<MovementsPage />, {
      route: '/history/movements?sku=HOOD-NVY-XL',
    });

    expect(
      await screen.findByText('2 movements for HOOD-NVY-XL in this period'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('SKU movement summary')).toHaveTextContent(
      '12 on hand · 2 reserved on this page',
    );
  });

  it('deep links order and stock-count references', async () => {
    const { container } = renderWithProviders(<MovementsPage />, {
      route: '/history/movements',
    });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    expect(
      container.querySelector(`a[href="/orders/new?order=${pendingOrder.id}"]`),
    ).not.toBeNull();
    expect(
      container.querySelector(`a[href="/stock/count?session=${completedCount.id}"]`),
    ).not.toBeNull();
  });
});
