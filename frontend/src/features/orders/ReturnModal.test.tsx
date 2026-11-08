import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ReturnModal } from './ReturnModal';
import { returnedOrder, shippedOrder } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';

describe('ReturnModal', () => {
  it('requires at least one return quantity', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ReturnModal order={shippedOrder} opened onClose={vi.fn()} onReturned={vi.fn()} />,
    );
    await screen.findByText('Returnable items');

    await user.click(screen.getByRole('button', { name: 'Process return' }));

    expect(
      await screen.findByText('Enter a return quantity for at least one item.'),
    ).toBeInTheDocument();
  });

  it('restocks sellable returned units and closes back to the order', async () => {
    const user = userEvent.setup();
    const onReturned = vi.fn();
    renderWithProviders(
      <ReturnModal order={shippedOrder} opened onClose={vi.fn()} onReturned={onReturned} />,
    );
    await screen.findByText('Returnable items');

    const quantity = screen.getByLabelText('Return quantity for HOOD-NVY-XL');
    await user.clear(quantity);
    await user.type(quantity, '1');
    await user.click(screen.getByRole('button', { name: 'Process return' }));

    const alert = (await screen.findByText('Return processed')).closest('[role="alert"]') as HTMLElement;
    expect(alert).toHaveTextContent('Restocked 1 sellable units; wrote off 0 damaged units.');
    expect(alert).toHaveTextContent('HOOD-NVY-XL · 1× sellable');

    await user.click(screen.getByRole('button', { name: 'Done' }));
    expect(onReturned).toHaveBeenCalledWith(
      expect.objectContaining({ orderNumber: shippedOrder.orderNumber }),
    );
  });

  it('writes off damaged returned units without restocking them', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ReturnModal order={shippedOrder} opened onClose={vi.fn()} onReturned={vi.fn()} />,
    );
    await screen.findByText('Returnable items');

    const quantity = screen.getByLabelText('Return quantity for HOOD-NVY-XL');
    await user.clear(quantity);
    await user.type(quantity, '1');
    await user.click(screen.getAllByLabelText('Condition for HOOD-NVY-XL')[0]);
    await user.click(await screen.findByText('Damaged — write off'));
    await user.click(screen.getByRole('button', { name: 'Process return' }));

    const alert = (await screen.findByText('Return processed')).closest('[role="alert"]') as HTMLElement;
    expect(alert).toHaveTextContent('Restocked 0 sellable units; wrote off 1 damaged units.');
    expect(alert).toHaveTextContent('HOOD-NVY-XL · 1× damaged');
  });

  it('caps the return quantity by units already returned', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ReturnModal order={returnedOrder} opened onClose={vi.fn()} onReturned={vi.fn()} />,
    );
    await screen.findByText('Returnable items');

    const input = await screen.findByLabelText('Return quantity for COF-ETH-250G');
    await user.clear(input);
    await user.type(input, '2');
    await user.tab();
    expect(input).toHaveValue('1');
  });
});
