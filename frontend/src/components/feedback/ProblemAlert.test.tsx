import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProblemAlert } from './ProblemAlert';
import { ApiError } from '../../lib/api/errors';
import { renderWithProviders } from '../../test/render';

function stockRefusal() {
  return new ApiError(409, {
    title: 'Insufficient Stock',
    status: 409,
    code: 'INSUFFICIENT_STOCK',
    detail:
      "You asked for 5 units of SKU 'HOOD-NVY-XL'. 8 are on the shelf and 6 are reserved for open orders, leaving 2 available to sell. Receive more stock, or reduce this order.",
    sku: 'HOOD-NVY-XL',
    requestedQuantity: 5,
    availableQuantity: 2,
    onHandQuantity: 8,
    reservedQuantity: 6,
    reservedBy: [
      {
        orderId: '00000000-0000-4000-8000-000000001042',
        orderNumber: 'ORD-2026-1042',
        reservedQuantity: 4,
        orderDate: '2026-10-29T10:00:00Z',
      },
      {
        orderId: '00000000-0000-4000-8000-000000001043',
        orderNumber: 'ORD-2026-1043',
        reservedQuantity: 2,
        orderDate: '2026-10-30T11:30:00Z',
      },
    ],
    actions: [
      { code: 'RECEIVE_STOCK', label: 'Receive more stock' },
      { code: 'REDUCE_QUANTITY', label: 'Reduce this order' },
      { code: 'VIEW_MOVEMENTS', label: 'View movements' },
      { code: 'VIEW_RESERVATIONS', label: 'View reservations' },
    ],
  });
}

describe('ProblemAlert', () => {
  it('renders the stock split, reservation holders, and known action links', () => {
    renderWithProviders(<ProblemAlert error={stockRefusal()} />);

    expect(screen.getByText(/8 on hand · 6 reserved · 2 available/)).toBeInTheDocument();
    expect(screen.getByText(/ORD-2026-1042 \(4\)/)).toBeInTheDocument();

    expect(screen.getByRole('link', { name: 'Receive more stock' })).toHaveAttribute(
      'href',
      '/stock/products?receiveSku=HOOD-NVY-XL',
    );
    expect(screen.getByRole('link', { name: 'View movements' })).toHaveAttribute(
      'href',
      '/history/movements?sku=HOOD-NVY-XL',
    );
    expect(screen.getByRole('link', { name: 'View reservations' })).toHaveAttribute(
      'href',
      '/stock/products?explainSku=HOOD-NVY-XL',
    );
  });

  it('renders quantity reduction as advice rather than a link', () => {
    renderWithProviders(<ProblemAlert error={stockRefusal()} />);

    expect(screen.getByText('Reduce this order')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Reduce this order' })).not.toBeInTheDocument();
  });

  it('links a retired refusal to the retired-products list', () => {
    const error = new ApiError(409, {
      title: 'Product Retired',
      status: 409,
      code: 'PRODUCT_RETIRED',
      detail: "SKU 'JKT-WAX-OLV-M' is retired and cannot be ordered.",
      sku: 'JKT-WAX-OLV-M',
      actions: [{ code: 'VIEW_RETIRED_PRODUCTS', label: 'View retired products' }],
    });

    renderWithProviders(<ProblemAlert error={error} />);

    expect(screen.getByRole('link', { name: 'View retired products' })).toHaveAttribute(
      'href',
      '/stock/products?retired=1',
    );
  });
});
