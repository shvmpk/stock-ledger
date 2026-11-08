import { Route, Routes } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { ProductFormPage } from './ProductFormPage';
import { products, sampleSwatchProduct } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import type { ProductInput } from '../../lib/api/types';

const hoodie = products[0];
const hoodieVariant = hoodie.variants[0];

function ProductFormRoutes() {
  return (
    <Routes>
      <Route path="/stock/products/new" element={<ProductFormPage />} />
      <Route path="/stock/products/:productId/edit" element={<ProductFormPage />} />
      <Route path="/stock/products" element={<div>Products destination</div>} />
    </Routes>
  );
}

type TestUser = ReturnType<typeof userEvent.setup>;

async function fillVariant(
  user: TestUser,
  index: number,
  variant: {
    sku: string;
    barcode?: string;
    optionValue: string;
    sellingPrice: string;
    costPrice: string;
    weightGrams: number;
    minStockThreshold: number;
  },
) {
  await user.type(screen.getAllByLabelText(/^SKU/)[index], variant.sku);
  if (variant.barcode) {
    await user.type(screen.getAllByLabelText('Barcode')[index], variant.barcode);
  }
  await user.type(screen.getAllByLabelText('Option name')[index], 'Size');
  await user.type(screen.getAllByLabelText('Option value')[index], variant.optionValue);
  await user.type(screen.getAllByLabelText(/^Retail price \(GBP\)/)[index], variant.sellingPrice);
  await user.type(screen.getAllByLabelText('Cost price (GBP)')[index], variant.costPrice);
  await user.type(screen.getAllByLabelText('Weight (grams)')[index], String(variant.weightGrams));
  await user.type(
    screen.getAllByLabelText('Reorder level')[index],
    String(variant.minStockThreshold),
  );
}

function captureCreate(payloads: ProductInput[]): void {
  server.use(
    http.post('/api/v1/products', async ({ request }) => {
      payloads.push((await request.json()) as ProductInput);
      return HttpResponse.json(
        {
          success: true,
          data: { ...hoodie, name: 'Merino Wool Crew Socks' },
          meta: { timestamp: '2026-10-30T10:00:00Z' },
        },
        { status: 201 },
      );
    }),
  );
}

describe('ProductFormPage', () => {
  it('adds a three-variant product and posts realistic catalogue data', async () => {
    const user = userEvent.setup();
    const payloads: ProductInput[] = [];
    captureCreate(payloads);

    renderWithProviders(<ProductFormRoutes />, { route: '/stock/products/new' });

    await user.type(screen.getByLabelText(/^Product name/), 'Merino Wool Crew Socks');
    await user.type(screen.getByLabelText('Category'), 'Accessories');
    await user.type(
      screen.getByLabelText('Description'),
      'Fine-gauge merino knit, reinforced heel and toe.',
    );

    await user.click(screen.getByRole('button', { name: 'Add variant' }));
    await user.click(screen.getByRole('button', { name: 'Add variant' }));

    await fillVariant(user, 0, {
      sku: 'SOCK-MRN-M',
      barcode: '5060123456718',
      optionValue: 'M',
      sellingPrice: '14.00',
      costPrice: '4.50',
      weightGrams: 80,
      minStockThreshold: 10,
    });
    await fillVariant(user, 1, {
      sku: 'SOCK-MRN-L',
      barcode: '5060123456725',
      optionValue: 'L',
      sellingPrice: '14.00',
      costPrice: '4.50',
      weightGrams: 85,
      minStockThreshold: 10,
    });
    await fillVariant(user, 2, {
      sku: 'SOCK-MRN-XL',
      barcode: '5060123456732',
      optionValue: 'XL',
      sellingPrice: '14.00',
      costPrice: '4.80',
      weightGrams: 90,
      minStockThreshold: 6,
    });

    await user.click(screen.getByRole('button', { name: 'Add product' }));

    expect(await screen.findByText('Products destination')).toBeInTheDocument();
    expect(payloads).toHaveLength(1);
    expect(payloads[0]).toMatchObject({
      name: 'Merino Wool Crew Socks',
      category: 'Accessories',
      description: 'Fine-gauge merino knit, reinforced heel and toe.',
      variants: [
        {
          sku: 'SOCK-MRN-M',
          barcode: '5060123456718',
          optionName: 'Size',
          optionValue: 'M',
          costPrice: '4.50',
          sellingPrice: '14.00',
          weightGrams: 80,
          minStockThreshold: 10,
        },
        {
          sku: 'SOCK-MRN-L',
          barcode: '5060123456725',
          optionValue: 'L',
          weightGrams: 85,
        },
        {
          sku: 'SOCK-MRN-XL',
          barcode: '5060123456732',
          optionValue: 'XL',
          costPrice: '4.80',
          weightGrams: 90,
          minStockThreshold: 6,
        },
      ],
    });
    expect(payloads[0].variants[0].id).toBeUndefined();
  }, 15000);

  it('loads an existing product and submits its variant ids so edits update instead of duplicate', async () => {
    const user = userEvent.setup();
    const payloads: ProductInput[] = [];
    server.use(
      http.put('/api/v1/products/:productId', async ({ request }) => {
        payloads.push((await request.json()) as ProductInput);
        return HttpResponse.json({
          success: true,
          data: { ...hoodie, name: 'Heavyweight Cotton Hoodie 2026' },
          meta: { timestamp: '2026-10-30T10:00:00Z' },
        });
      }),
    );

    renderWithProviders(<ProductFormRoutes />, {
      route: `/stock/products/${hoodie.id}/edit`,
    });

    expect(await screen.findByDisplayValue('Heavyweight Cotton Hoodie')).toBeInTheDocument();
    expect(screen.getAllByLabelText(/^SKU/)[0]).toHaveValue('HOOD-NVY-XL');
    expect(screen.getAllByLabelText(/^SKU/)[1]).toHaveValue('HOOD-NVY-M');

    const name = screen.getByLabelText(/^Product name/);
    await user.clear(name);
    await user.type(name, 'Heavyweight Cotton Hoodie 2026');

    const price = screen.getAllByLabelText(/^Retail price \(GBP\)/)[0];
    await user.clear(price);
    await user.type(price, '62.00');

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Products destination')).toBeInTheDocument();
    expect(payloads).toHaveLength(1);
    expect(payloads[0].name).toBe('Heavyweight Cotton Hoodie 2026');
    expect(payloads[0].variants[0]).toMatchObject({
      id: hoodieVariant.id,
      sku: 'HOOD-NVY-XL',
      sellingPrice: '62.00',
    });
  });

  it('keeps an unknown cost, weight and reorder level empty and saves them as unknown', async () => {
    const user = userEvent.setup();
    const payloads: ProductInput[] = [];
    server.use(
      http.put('/api/v1/products/:productId', async ({ request }) => {
        payloads.push((await request.json()) as ProductInput);
        return HttpResponse.json({
          success: true,
          data: sampleSwatchProduct,
          meta: { timestamp: '2026-10-31T10:00:00Z' },
        });
      }),
    );

    renderWithProviders(<ProductFormRoutes />, {
      route: `/stock/products/${sampleSwatchProduct.id}/edit`,
    });

    expect(await screen.findByDisplayValue('Linen Sample Swatch Set')).toBeInTheDocument();
    expect(screen.getByLabelText('Cost price (GBP)')).toHaveValue('');
    expect(screen.getByLabelText('Weight (grams)')).toHaveValue('');
    expect(screen.getByLabelText('Reorder level')).toHaveValue('');

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Products destination')).toBeInTheDocument();
    expect(payloads).toHaveLength(1);
    expect(payloads[0].variants[0].costPrice).toBeUndefined();
    expect(payloads[0].variants[0].weightGrams).toBeUndefined();
    expect(payloads[0].variants[0].minStockThreshold).toBeUndefined();
  });

  it('puts a duplicate-SKU refusal under the variant that caused it', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/v1/products', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/validation-failed',
            title: 'Validation Failed',
            status: 400,
            detail: 'One or more product fields are invalid.',
            code: 'VALIDATION_FAILED',
            timestamp: '2026-10-30T10:00:00Z',
            invalidParams: [
              {
                field: 'variants[1].sku',
                message: "SKU 'SOCK-MRN-M' is already in use. Choose a different SKU.",
              },
            ],
          },
          { status: 400 },
        ),
      ),
    );

    renderWithProviders(<ProductFormRoutes />, { route: '/stock/products/new' });

    await user.type(screen.getByLabelText(/^Product name/), 'Merino Wool Crew Socks');
    await user.click(screen.getByRole('button', { name: 'Add variant' }));
    await fillVariant(user, 0, {
      sku: 'SOCK-MRN-M',
      optionValue: 'M',
      sellingPrice: '14.00',
      costPrice: '4.50',
      weightGrams: 80,
      minStockThreshold: 10,
    });
    await fillVariant(user, 1, {
      sku: 'sock-mrn-m',
      optionValue: 'L',
      sellingPrice: '14.00',
      costPrice: '4.50',
      weightGrams: 85,
      minStockThreshold: 10,
    });

    await user.click(screen.getByRole('button', { name: 'Add product' }));

    expect(
      await screen.findByText("SKU 'SOCK-MRN-M' is already in use. Choose a different SKU."),
    ).toBeInTheDocument();
    expect(screen.getAllByLabelText(/^SKU/)[1]).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getAllByLabelText(/^SKU/)[0]).not.toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('Products destination')).not.toBeInTheDocument();
  });

  it('catches a blank name, a missing variant and a negative cost before calling the server', async () => {
    const user = userEvent.setup();
    let posted = false;
    server.use(
      http.post('/api/v1/products', () => {
        posted = true;
        return HttpResponse.json({}, { status: 500 });
      }),
    );

    renderWithProviders(<ProductFormRoutes />, { route: '/stock/products/new' });

    await user.click(screen.getByRole('button', { name: 'Add product' }));

    expect(await screen.findByText('Product name is required.')).toBeInTheDocument();
    expect(screen.getByText('SKU is required.')).toBeInTheDocument();
    expect(screen.getByText('Retail price is required.')).toBeInTheDocument();
    expect(posted).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Remove variant 1' }));
    await user.click(screen.getByRole('button', { name: 'Add product' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Add at least one variant.');

    await user.type(screen.getByLabelText(/^Product name/), 'Merino Wool Crew Socks');
    await user.click(screen.getByRole('button', { name: 'Add variant' }));
    await fillVariant(user, 0, {
      sku: 'SOCK-MRN-M',
      optionValue: 'M',
      sellingPrice: '14.00',
      costPrice: '-1.00',
      weightGrams: 80,
      minStockThreshold: 10,
    });

    await user.click(screen.getByRole('button', { name: 'Add product' }));

    expect(await screen.findByText('Cost cannot be negative.')).toBeInTheDocument();
    await waitFor(() => expect(posted).toBe(false));
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<ProductFormRoutes />, {
      route: '/stock/products/new',
    });

    expect(await axe(container)).toHaveNoViolations();
  });
});
