import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { ReceiveStockDrawer } from './ReceiveStockDrawer';
import { products, waitingOrder } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

const noop = vi.fn();

describe('ReceiveStockDrawer', () => {
  it('requires a variant to be selected before submitting', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReceiveStockDrawer opened onClose={noop} />);

    // Wait for catalog to finish loading — the Select's placeholder appears
    const input = await screen.findByPlaceholderText('Search by product, SKU, or barcode');
    const drawer = input.closest('[role="dialog"]') as HTMLElement;

    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    expect(
      await within(drawer).findByText('Choose a product variant before receiving stock.'),
    ).toBeInTheDocument();
  });

  it('records a supplier delivery and shows the updated ledger numbers', async () => {
    const user = userEvent.setup();
    const variant = products[0].variants[0]; // HOOD-NVY-XL: onHand 12, reserved 2, available 10
    renderWithProviders(
      <ReceiveStockDrawer
        opened
        onClose={noop}
        variantId={variant.id}
        variantLabel="Heavyweight Cotton Hoodie — XL · HOOD-NVY-XL"
      />,
    );

    // Wait for catalog to load — current stock hint appears after products resolve
    const hint = await screen.findByText(/Current on hand: 12/);
    const drawer = hint.closest('[role="dialog"]') as HTMLElement;

    expect(within(drawer).getByText('Heavyweight Cotton Hoodie — XL · HOOD-NVY-XL')).toBeInTheDocument();

    const qty = within(drawer).getByLabelText(/Quantity received/);
    await user.clear(qty);
    await user.type(qty, '8');

    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    // Success: on hand 12 → 20, available now 18 (20 − 2 reserved)
    expect(await within(drawer).findByText('Received')).toBeInTheDocument();
    expect(within(drawer).getByText(/HOOD-NVY-XL/)).toBeInTheDocument();
    expect(within(drawer).getByText(/on hand 12/)).toBeInTheDocument();
    expect(within(drawer).getByText(/20/)).toBeInTheDocument();
    expect(within(drawer).getByText(/available now 18/)).toBeInTheDocument();

    // No waiting orders for this variant
    expect(
      within(drawer).getByText(
        'No waiting orders needed this stock. The units are available for new orders.',
      ),
    ).toBeInTheDocument();

    // Toast notification fired
    expect(await screen.findByText('Stock received')).toBeInTheDocument();
  });

  it('shows allocated waiting-order numbers when the new stock satisfies a backlog', async () => {
    const user = userEvent.setup();
    // COF-ETH-1KG: onHand 0, reserved 0 — MSW handler allocates waitingOrder when this SKU is received
    const variant = products[1].variants[1]; // COF-ETH-1KG
    renderWithProviders(
      <ReceiveStockDrawer
        opened
        onClose={noop}
        variantId={variant.id}
        variantLabel="Pantry Coffee — Ethiopia Guji — 1 kg · COF-ETH-1KG"
      />,
    );

    const hint = await screen.findByText(/Current on hand: 0/);
    const drawer = hint.closest('[role="dialog"]') as HTMLElement;

    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    // Allocated order number badge is shown
    expect(await within(drawer).findByText('Allocated to waiting orders:')).toBeInTheDocument();
    expect(within(drawer).getByText(waitingOrder.orderNumber)).toBeInTheDocument(); // ORD-2026-0110
    expect(within(drawer).getByRole('link', { name: 'Open the New queue' })).toBeInTheDocument();
  });

  it('picks a variant from the combobox when no variantId is pre-filled', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReceiveStockDrawer opened onClose={noop} />);

    // Wait for catalog load — placeholder input appears when products are ready
    const input = await screen.findByPlaceholderText('Search by product, SKU, or barcode');
    const drawer = input.closest('[role="dialog"]') as HTMLElement;

    // Before selecting, the current-stock hint is absent
    expect(within(drawer).queryByText(/Current on hand/)).not.toBeInTheDocument();

    await user.click(input);
    await user.type(input, 'POUR-SET');

    const option = await screen.findByText(/Stoneware Pour-Over Set — Sand · POUR-SET-SND/);
    await user.click(option);

    // Current stock numbers for the selected variant appear
    expect(await within(drawer).findByText(/Current on hand: 3/)).toBeInTheDocument();
  });

  it('surfaces an API error without losing the form', async () => {
    server.use(
      http.post('/api/v1/stock/receive', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/not-found',
            title: 'Not Found',
            status: 404,
            detail: 'Product variant was not found.',
            code: 'NOT_FOUND',
            timestamp: new Date().toISOString(),
            invalidParams: [],
          },
          { status: 404 },
        ),
      ),
    );

    const user = userEvent.setup();
    const variant = products[0].variants[0]; // HOOD-NVY-XL
    renderWithProviders(
      <ReceiveStockDrawer opened onClose={noop} variantId={variant.id} variantLabel="HOOD-NVY-XL" />,
    );

    const hint = await screen.findByText(/Current on hand/);
    const drawer = hint.closest('[role="dialog"]') as HTMLElement;
    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    expect(await within(drawer).findByText('Could not receive stock')).toBeInTheDocument();
    expect(within(drawer).getByText('Product variant was not found.')).toBeInTheDocument();

    // Form remains intact — submit button still present
    expect(within(drawer).getByRole('button', { name: 'Receive stock' })).toBeInTheDocument();
  });

  it('has no detectable accessibility violations on the open form', async () => {
    const variant = products[0].variants[0];
    const { container } = renderWithProviders(
      <ReceiveStockDrawer opened onClose={noop} variantId={variant.id} variantLabel="HOOD-NVY-XL" />,
    );
    await screen.findByText(/Current on hand/);
    expect(await axe(container)).toHaveNoViolations();
  });
});
