import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { AllProductsPage } from './AllProductsPage';
import { products, retiredProduct, staffUser } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import type { Product } from '../../lib/api/types';

function rowForSku(sku: string): HTMLElement {
  const row = screen
    .getAllByRole('row')
    .find((candidate) => within(candidate).queryByText(sku));
  if (!row) throw new Error(`No row found for ${sku}`);
  return row;
}

function withoutCost(): Product[] {
  return products.map((product) => ({
    ...product,
    variants: product.variants.map((variant) => ({ ...variant, costPrice: undefined })),
  }));
}

async function openActionsMenu(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
): Promise<HTMLElement> {
  await user.click(screen.getAllByRole('button', { name: `Actions for ${label}` })[0]);
  return screen.findByRole('menu', { hidden: true });
}

function chooseMenuItem(menu: HTMLElement, name: string) {
  fireEvent.click(within(menu).getByRole('menuitem', { name, hidden: true }));
}

describe('AllProductsPage', () => {
  it('shows catalog totals and expands a product to reveal its ledger', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);

    expect(await screen.findByRole('heading', { name: 'Stock' })).toBeInTheDocument();
    expect(screen.getByLabelText('Catalog summary')).toHaveTextContent('Products');
    expect((await screen.findAllByText('Heavyweight Cotton Hoodie')).length).toBeGreaterThan(0);
    expect((await screen.findAllByText('Pantry Coffee — Ethiopia Guji')).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Expand Heavyweight Cotton Hoodie' }));

    expect(screen.getAllByText('HOOD-NVY-XL').length).toBeGreaterThan(0);
    expect(screen.getAllByText('HOOD-NVY-M').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Size').length).toBeGreaterThan(0);
  });

  it('filters the catalog by SKU and opens the receive drawer for that variant', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Pantry Coffee — Ethiopia Guji');

    await user.type(screen.getByLabelText('Search catalog'), 'COF-ETH-1KG');

    expect((await screen.findAllByText('Pantry Coffee — Ethiopia Guji')).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryAllByText('Heavyweight Cotton Hoodie')).toHaveLength(0));
    expect(screen.getAllByText('COF-ETH-1KG').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Receive' }));
    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('Pantry Coffee — Ethiopia Guji — 1 kg · COF-ETH-1KG')).toBeInTheDocument();

    const quantity = within(drawer).getByLabelText(/Quantity received/);
    await user.clear(quantity);
    await user.type(quantity, '5');
    await user.type(within(drawer).getByLabelText('Notes'), 'Roaster delivery RO-4471');
    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    expect(await within(drawer).findByText('Received')).toBeInTheDocument();
    expect(within(drawer).getByText(/COF-ETH-1KG/)).toBeInTheDocument();
    expect(within(drawer).getByText('ORD-2026-0110')).toBeInTheDocument();
    expect(within(drawer).getByText(/Allocated to waiting orders/)).toBeInTheDocument();
  });

  it('opens an empty receive drawer from the page action and validates the variant', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Pantry Coffee — Ethiopia Guji');

    await user.click(screen.getByRole('button', { name: 'Receive stock' }));
    const drawer = await screen.findByRole('dialog');

    expect(within(drawer).getByText(/Record a supplier delivery/)).toBeInTheDocument();
    await user.click(within(drawer).getByRole('button', { name: 'Receive stock' }));

    expect(await within(drawer).findByText('Choose a product variant before receiving stock.')).toBeInTheDocument();
  });

  it('shows category and variant visual identity markers', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    const avatars = screen.getAllByTestId('variant-avatar');
    expect(avatars.map((avatar) => avatar.getAttribute('data-sku'))).toEqual(
      expect.arrayContaining(['HOOD-NVY-XL', 'HOOD-NVY-M']),
    );

    const apparelIcons = screen
      .getAllByTestId('category-icon')
      .filter((icon) => icon.getAttribute('data-category') === 'Apparel');
    expect(apparelIcons.length).toBeGreaterThan(0);
    expect(apparelIcons[0].querySelector('svg')).toHaveClass('tabler-icon-hanger');

    await user.click(screen.getByLabelText('Category', { selector: 'input' }));
    const option = await screen.findByRole('option', { name: 'Apparel', hidden: true });
    expect(within(option).getByTestId('category-icon')).toHaveAttribute(
      'data-category',
      'Apparel',
    );
  });

  it('shows an em dash where the server withholds cost and margin from staff', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('/api/v1/products', () =>
        HttpResponse.json({
          success: true,
          data: withoutCost(),
          meta: { timestamp: '2026-10-28T18:00:00Z' },
        }),
      ),
    );

    renderWithProviders(<AllProductsPage />, { user: staffUser });
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    await user.click(screen.getByRole('button', { name: 'Expand Heavyweight Cotton Hoodie' }));

    const cells = within(rowForSku('HOOD-NVY-XL')).getAllByRole('cell');

    expect(cells[1]).toHaveTextContent('—');
    expect(cells[2]).toHaveTextContent('£58.00');
    expect(cells[3]).toHaveTextContent('—');
    expect(screen.getByRole('columnheader', { name: 'Cost' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Margin' })).toBeInTheDocument();
  });

  it('shows cost and margin to an owner', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    await user.click(screen.getByRole('button', { name: 'Expand Heavyweight Cotton Hoodie' }));

    const cells = within(rowForSku('HOOD-NVY-XL')).getAllByRole('cell');

    expect(cells[1]).toHaveTextContent('£24.00');
    expect(cells[2]).toHaveTextContent('£58.00');
    expect(cells[3]).toHaveTextContent('58.6%');
  });

  it('offers an owner the Add product and Edit product routes', async () => {
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    expect(screen.getByRole('link', { name: 'Add product' })).toHaveAttribute(
      'href',
      '/stock/products/new',
    );
    expect(
      screen.getByRole('link', { name: 'Edit Heavyweight Cotton Hoodie' }),
    ).toHaveAttribute('href', `/stock/products/${products[0].id}/edit`);
    expect(screen.getAllByRole('link', { name: 'Edit product' }).length).toBeGreaterThan(0);
  });

  it('does not offer staff any catalogue editing controls', async () => {
    renderWithProviders(<AllProductsPage />, { user: staffUser });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    expect(screen.queryByRole('link', { name: 'Add product' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Edit Heavyweight Cotton Hoodie' }),
    ).not.toBeInTheDocument();
    expect(screen.queryAllByRole('link', { name: 'Edit product' })).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Actions for/ })).not.toBeInTheDocument();
  });

  it('keeps retired products out of the working catalogue until the owner asks to see them', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    expect(screen.queryByText('Waxed Canvas Field Jacket')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Show retired'));

    expect((await screen.findAllByText('Waxed Canvas Field Jacket')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Retired').length).toBeGreaterThan(0);
    expect(screen.getAllByText(retiredProduct.variants[0].sku).length).toBeGreaterThan(0);
  });

  it('opens a receive drawer prefilled from a receiveSku link', async () => {
    renderWithProviders(<AllProductsPage />, {
      route: '/stock/products?receiveSku=COF-ETH-1KG',
    });

    const drawer = await screen.findByRole('dialog');
    expect(
      await within(drawer).findByText('Pantry Coffee — Ethiopia Guji — 1 kg · COF-ETH-1KG'),
    ).toBeInTheDocument();
    expect(await within(drawer).findByText(/Current on hand: 0/)).toBeInTheDocument();
  });

  it('opens the explanation drawer from an explainSku link', async () => {
    renderWithProviders(<AllProductsPage />, {
      route: '/stock/products?explainSku=HOOD-NVY-XL',
    });

    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('Why this number?')).toBeInTheDocument();
    expect(
      await within(drawer).findByText(
        'There are 12 units on the shelf, 2 reserved for open orders, leaving 10 available to sell.',
      ),
    ).toBeInTheDocument();
  });

  it('shows retired products when the retired=1 link is followed', async () => {
    renderWithProviders(<AllProductsPage />, {
      route: '/stock/products?retired=1',
    });

    expect(await screen.findByLabelText('Show retired')).toBeChecked();
    expect((await screen.findAllByText('Waxed Canvas Field Jacket')).length).toBeGreaterThan(0);
  });

  it('opens the explanation from a variant row', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    await user.click(screen.getByRole('button', { name: 'Expand Heavyweight Cotton Hoodie' }));

    await user.click(
      within(rowForSku('HOOD-NVY-XL')).getByRole('button', { name: 'Why this number?' }),
    );

    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('Why this number?')).toBeInTheDocument();
    expect(within(drawer).getByText('Heavyweight Cotton Hoodie')).toBeInTheDocument();
  });

  it('lets an owner retire a product, see it behind the retired filter, and restore it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    const retireMenu = await openActionsMenu(user, 'Heavyweight Cotton Hoodie');
    chooseMenuItem(retireMenu, 'Retire product');

    const dialog = await screen.findByRole('dialog');    expect(within(dialog).getByText('Retire Heavyweight Cotton Hoodie?')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Retire product' }));

    await waitFor(() =>
      expect(screen.queryAllByText('Heavyweight Cotton Hoodie')).toHaveLength(0),
    );

    await user.click(screen.getByLabelText('Show retired'));
    expect((await screen.findAllByText('Heavyweight Cotton Hoodie')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Retired').length).toBeGreaterThan(0);

    const restoreMenu = await openActionsMenu(user, 'Heavyweight Cotton Hoodie');
    chooseMenuItem(restoreMenu, 'Restore product');
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Restore product' }),
    );

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const restoredMenu = await openActionsMenu(user, 'Heavyweight Cotton Hoodie');
    expect(
      within(restoredMenu).getByRole('menuitem', { name: 'Retire product', hidden: true }),
    ).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByLabelText('Show retired'));
    await waitFor(() =>
      expect(screen.queryAllByText('Waxed Canvas Field Jacket')).toHaveLength(0),
    );
    expect(screen.getAllByText('Heavyweight Cotton Hoodie').length).toBeGreaterThan(0);
  });

  it('lets an owner retire a single variant and blocks receiving it until restored', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    await user.click(screen.getByRole('button', { name: 'Expand Heavyweight Cotton Hoodie' }));

    const variantMenu = await openActionsMenu(user, 'HOOD-NVY-XL');
    chooseMenuItem(variantMenu, 'Retire variant');
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Retire variant' }),
    );

    await waitFor(() => expect(screen.queryAllByText('HOOD-NVY-XL')).toHaveLength(0));
    expect(screen.getAllByText('HOOD-NVY-M').length).toBeGreaterThan(0);

    await user.click(screen.getByLabelText('Show retired'));
    expect((await screen.findAllByText('HOOD-NVY-XL')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Retired').length).toBeGreaterThan(0);

    const retiredRow = rowForSku('HOOD-NVY-XL');
    expect(within(retiredRow).queryByRole('button', { name: 'Receive' })).not.toBeInTheDocument();
    expect(within(retiredRow).queryByRole('button', { name: 'Count' })).not.toBeInTheDocument();
  });

  it('shows an em dash and no low-stock warning when cost and reorder level are unknown', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Linen Sample Swatch Set');

    await user.click(screen.getByRole('button', { name: 'Expand Linen Sample Swatch Set' }));

    const row = rowForSku('SWATCH-LIN-01');
    expect(within(row).getAllByText('—').length).toBeGreaterThanOrEqual(2);
    expect(within(row).getAllByText('In stock').length).toBeGreaterThan(0);
    expect(within(row).queryByText('Low')).not.toBeInTheDocument();
  });

  it('has no detectable accessibility violations with retired items visible', async () => {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    await user.click(screen.getByLabelText('Show retired'));
    await screen.findAllByText('Waxed Canvas Field Jacket');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<AllProductsPage />);
    await screen.findAllByText('Heavyweight Cotton Hoodie');
    expect(await axe(container)).toHaveNoViolations();
  });
});
