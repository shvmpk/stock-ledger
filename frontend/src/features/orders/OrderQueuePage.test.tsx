import { Route, Routes } from 'react-router-dom';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { OrderQueuePage } from './OrderQueuePage';
import { renderWithProviders } from '../../test/render';

function QueueRoutes() {
  return (
    <Routes>
      <Route path="/orders/:queue" element={<OrderQueuePage />} />
    </Routes>
  );
}

describe('OrderQueuePage', () => {
  it('maps the queue to its order status and opens the order drawer', async () => {
    const user = userEvent.setup();
    renderWithProviders(<QueueRoutes />, { route: '/orders/new' });

    expect(await screen.findByText('New orders')).toBeInTheDocument();
    expect((await screen.findAllByText('ORD-2026-0112')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Harriet Wells').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Manual').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Open' }));
    const drawer = await screen.findByRole('dialog');

    expect(within(drawer).getByText('ORD-2026-0112')).toBeInTheDocument();
    expect(within(drawer).getByText('Harriet Wells')).toBeInTheDocument();
    expect(within(drawer).getByText(/18 Marlborough Crescent/)).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: 'Pack order' })).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: 'Ship' })).toBeInTheDocument();
  });

  it('shows waiting-for-stock orders with their ingestion source', async () => {
    renderWithProviders(<QueueRoutes />, { route: '/orders/waiting' });

    expect(await screen.findByText('Waiting for stock')).toBeInTheDocument();
    expect((await screen.findAllByText('ORD-2026-0110')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('CSV import').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/COF-ETH-1KG/).length).toBeGreaterThan(0);
  });

  it('opens the stock explanation for an order line', async () => {
    const user = userEvent.setup();
    renderWithProviders(<QueueRoutes />, { route: '/orders/new' });
    await screen.findAllByText('ORD-2026-0112');

    await user.click(screen.getByRole('button', { name: 'Open' }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: 'Why this number?' }));

    const explanation = await screen.findByText(
      'There are 12 units on the shelf, 2 reserved for open orders, leaving 10 available to sell.',
    );
    expect(explanation).toBeInTheDocument();
    expect(screen.getByText('Reserved for orders')).toBeInTheDocument();
  });

  it('packs a pending order and updates the drawer status', async () => {
    const user = userEvent.setup();
    renderWithProviders(<QueueRoutes />, { route: '/orders/new' });
    await screen.findAllByText('ORD-2026-0112');

    await user.click(screen.getByRole('button', { name: 'Open' }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: 'Pack order' }));

    expect(await screen.findByText('Order packed')).toBeInTheDocument();
    expect(await within(drawer).findByText('Ready to ship')).toBeInTheDocument();
  });

  it('cancels a pending order with an operator reason', async () => {
    const user = userEvent.setup();
    renderWithProviders(<QueueRoutes />, { route: '/orders/new' });
    await screen.findAllByText('ORD-2026-0112');

    await user.click(screen.getByRole('button', { name: 'Open' }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: 'Cancel order' }));

    const cancelDialog = (await screen.findByText(/Cancelling releases any reserved stock/)).closest(
      '[role="dialog"]',
    ) as HTMLElement;
    const reason = within(cancelDialog).getByLabelText(/Reason/);
    await user.clear(reason);
    await user.type(reason, 'Customer found a better price');
    await user.click(within(cancelDialog).getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByText('Order cancelled')).toBeInTheDocument();
    expect(await within(drawer).findByText('Cancelled')).toBeInTheDocument();
  });

  it('shows the return history for a partially returned order', async () => {
    const user = userEvent.setup();
    renderWithProviders(<QueueRoutes />, {
      route: '/orders/returns?status=PARTIALLY_RETURNED',
    });

    expect(await screen.findByText('Returns & cancellations')).toBeInTheDocument();
    await screen.findAllByText('ORD-2026-0101');
    await user.click(screen.getByRole('button', { name: 'Open' }));

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Customer return — grind too coarse')).toBeInTheDocument();
    expect(within(drawer).getByText('Restocked')).toBeInTheDocument();
    expect(within(drawer).getAllByText('COF-ETH-250G').length).toBeGreaterThan(0);
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<QueueRoutes />, { route: '/orders/new' });
    await screen.findAllByText('ORD-2026-0112');
    expect(await axe(container)).toHaveNoViolations();
  });
});
