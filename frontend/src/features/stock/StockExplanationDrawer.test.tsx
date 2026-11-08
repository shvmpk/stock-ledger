import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { StockExplanationDrawer } from './StockExplanationDrawer';
import { pendingOrder, products, stockExplanation } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

const noop = vi.fn();
const hoodVariant = products[0].variants[0];

describe('StockExplanationDrawer', () => {
  it('explains the figures, reservations, and recent movements in plain words', async () => {
    renderWithProviders(
      <StockExplanationDrawer variantId={hoodVariant.id} onClose={noop} />,
    );

    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('Why this number?')).toBeInTheDocument();
    expect(await within(drawer).findByText('Heavyweight Cotton Hoodie')).toBeInTheDocument();
    const figures = within(drawer).getByLabelText('Stock explanation figures');
    expect(within(figures).getByText('12')).toBeInTheDocument();
    expect(within(figures).getByText('on hand')).toBeInTheDocument();
    expect(within(figures).getByText('2')).toBeInTheDocument();
    expect(within(figures).getByText('reserved')).toBeInTheDocument();
    expect(within(figures).getByText('10')).toBeInTheDocument();
    expect(within(figures).getByText('available')).toBeInTheDocument();

    expect(within(drawer).getByText(stockExplanation.sentences[0])).toBeInTheDocument();
    expect(
      within(drawer).getByRole('link', { name: pendingOrder.orderNumber }),
    ).toHaveAttribute('href', `/orders/new?order=${pendingOrder.id}`);
    expect(within(drawer).getByText('Reserved for orders')).toBeInTheDocument();
    expect(within(drawer).getByText('Received')).toBeInTheDocument();
    expect(within(drawer).getByRole('link', { name: 'View all movements' })).toHaveAttribute(
      'href',
      '/history/movements?sku=HOOD-NVY-XL',
    );
  });

  it('shows an error state when the explanation cannot be loaded', async () => {
    server.use(
      http.get('/api/v1/stock/variants/:variantId/explanation', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/resource-not-found',
            title: 'Not Found',
            status: 404,
            detail: 'ProductVariant was not found.',
            code: 'RESOURCE_NOT_FOUND',
            timestamp: new Date().toISOString(),
            invalidParams: [],
          },
          { status: 404 },
        ),
      ),
    );

    renderWithProviders(
      <StockExplanationDrawer variantId={hoodVariant.id} onClose={noop} />,
    );

    expect(await screen.findByText('Explanation unavailable')).toBeInTheDocument();
    expect(screen.getByText('ProductVariant was not found.')).toBeInTheDocument();
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(
      <StockExplanationDrawer variantId={hoodVariant.id} onClose={noop} />,
    );
    await screen.findByText(stockExplanation.sentences[0]);
    expect(await axe(container)).toHaveNoViolations();
  });
});
