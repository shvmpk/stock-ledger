import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { ManualOrderModal } from './ManualOrderModal';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

async function chooseVariant(user: ReturnType<typeof userEvent.setup>, sku: string) {
  const select = await screen.findByPlaceholderText('Search by product, SKU, or barcode');
  await user.click(select);
  await user.type(select, sku);
  await user.click(await screen.findByText(new RegExp(sku)));
}

async function fillCustomer(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Customer name/), 'Freja Lindqvist');
  await user.type(
    screen.getByLabelText(/Shipping address/),
    'Kungsgatan 14{enter}111 43 Stockholm{enter}Sweden',
  );
}

describe('ManualOrderModal', () => {
  it('validates required customer and line fields before calling the API', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ManualOrderModal opened onClose={vi.fn()} onCreated={vi.fn()} />,
    );

    await user.click(screen.getByRole('button', { name: 'Create order' }));

    expect(await screen.findByText('Customer name is required.')).toBeInTheDocument();
    expect(screen.getByText('Shipping address is required.')).toBeInTheDocument();
    expect(screen.getByText('Choose a product variant.')).toBeInTheDocument();
    expect(screen.getByText('Enter a valid unit price.')).toBeInTheDocument();
  });

  it('rejects a variant with no available stock before submitting', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ManualOrderModal opened onClose={vi.fn()} onCreated={vi.fn()} />,
    );

    await fillCustomer(user);
    await chooseVariant(user, 'COF-ETH-1KG');
    await user.click(screen.getByRole('button', { name: 'Create order' }));

    expect(await screen.findByText('Only 0 available for COF-ETH-1KG.')).toBeInTheDocument();
  });

  it('creates a manual order and reserves its stock', async () => {
    const user = userEvent.setup();
    const onCreated = vi.fn();
    renderWithProviders(
      <ManualOrderModal opened onClose={vi.fn()} onCreated={onCreated} />,
    );

    await fillCustomer(user);
    await chooseVariant(user, 'HOOD-NVY-XL');
    expect(await screen.findByText(/58.00 · 10 available/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create order' }));

    expect(await screen.findByText('Manual order created')).toBeInTheDocument();
    expect(onCreated).toHaveBeenCalledWith(
      expect.objectContaining({ orderNumber: 'ORD-2026-0114', status: 'PENDING' }),
    );
  });

  it('shows the RFC 7807 insufficient-stock rejection', async () => {
    server.use(
      http.post('/api/v1/orders', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/insufficient-stock',
            title: 'Insufficient Stock',
            status: 409,
            detail: 'Cannot reserve 2 units of HOOD-NVY-XL. Only 1 unit available.',
            code: 'INSUFFICIENT_STOCK',
            timestamp: '2026-10-23T09:00:00Z',
            invalidParams: [],
          },
          { status: 409 },
        ),
      ),
    );

    const user = userEvent.setup();
    const onCreated = vi.fn();
    renderWithProviders(
      <ManualOrderModal opened onClose={vi.fn()} onCreated={onCreated} />,
    );

    await fillCustomer(user);
    await chooseVariant(user, 'HOOD-NVY-XL');
    await user.click(screen.getByRole('button', { name: 'Create order' }));

    expect(await screen.findByText('Manual order rejected')).toBeInTheDocument();
    expect(
      screen.getByText('Cannot reserve 2 units of HOOD-NVY-XL. Only 1 unit available.'),
    ).toBeInTheDocument();
    expect(screen.getByText('INSUFFICIENT_STOCK')).toBeInTheDocument();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('has no detectable accessibility violations', async () => {
    renderWithProviders(<ManualOrderModal opened onClose={vi.fn()} onCreated={vi.fn()} />);
    await screen.findByPlaceholderText('Search by product, SKU, or barcode');
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
