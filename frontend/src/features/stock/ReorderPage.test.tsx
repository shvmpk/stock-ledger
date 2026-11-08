import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { ReorderPage } from './ReorderPage';
import { renderWithProviders, staffUser } from '../../test/render';

function rowForSku(sku: string): HTMLElement {
  const row = screen
    .getAllByRole('row')
    .find((candidate) => within(candidate).queryByText(sku));
  if (!row) throw new Error(`No row found for ${sku}`);
  return row;
}

describe('ReorderPage', () => {
  it('shows the buying assumptions and the urgency-sorted reorder list', async () => {
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });

    expect(await screen.findByRole('heading', { name: 'Reorder' })).toBeInTheDocument();
    expect((await screen.findAllByText('Pantry Coffee — Ethiopia Guji')).length).toBeGreaterThan(0);
    expect(screen.getByText(/14-day supplier lead time/)).toBeInTheDocument();
    expect(screen.getByText(/last 90 days/)).toBeInTheDocument();

    const summary = screen.getByLabelText('Reorder summary');
    expect(summary).toHaveTextContent('3');
    expect(summary).toHaveTextContent('39');

    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('COF-ETH-1KG')).toBeInTheDocument();
    expect(within(rows[1]).getByText('POUR-SET-SND')).toBeInTheDocument();
    expect(within(rows[2]).getByText('HOOD-NVY-M')).toBeInTheDocument();
  });

  it('filters the list to variants that are completely out of stock', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    await user.click(screen.getByText('Out of stock (1)'));

    expect((await screen.findAllByText('Pantry Coffee — Ethiopia Guji')).length).toBeGreaterThan(0);
    expect(screen.queryByText('Heavyweight Cotton Hoodie')).not.toBeInTheDocument();
    expect(screen.queryByText('Stoneware Pour-Over Set')).not.toBeInTheDocument();
  });

  it('traces a suggested quantity to the shipped orders and returns behind it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    await user.click(within(rowForSku('HOOD-NVY-M')).getByRole('button', { name: 'Trace' }));

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Shipped orders')).toBeInTheDocument();

    const summary = within(drawer).getByLabelText('Demand trace summary');
    expect(summary).toHaveTextContent('60');
    expect(summary).toHaveTextContent('6');
    expect(summary).toHaveTextContent('54');

    expect(within(drawer).getAllByText('ORD-2026-1078').length).toBeGreaterThanOrEqual(2);
    expect(within(drawer).getByText('ORD-2026-1042')).toBeInTheDocument();
    expect(within(drawer).getByText('Sellable')).toBeInTheDocument();
  });

  it('opens the stock explanation for a reorder candidate', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findAllByText('Heavyweight Cotton Hoodie');

    await user.click(
      within(rowForSku('POUR-SET-SND')).getByRole('button', { name: 'Why this number?' }),
    );

    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('Why this number?')).toBeInTheDocument();
    expect(within(drawer).getByText(/POUR-SET-SND/)).toBeInTheDocument();
  });

  it('lets an owner adjust the supplier lead time and applies it to the guidance', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findByRole('heading', { name: 'Reorder' });

    await user.click(await screen.findByRole('button', { name: 'Edit lead time' }));
    const modal = await screen.findByRole('dialog', { name: /Reorder assumptions/ });
    const leadTime = within(modal).getByLabelText(/Supplier lead time/);
    await user.clear(leadTime);
    await user.type(leadTime, '21');
    await user.click(within(modal).getByRole('button', { name: 'Save lead time' }));

    expect(await screen.findByText('Lead time saved')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect((await screen.findAllByText(/21-day supplier lead time/)).length).toBeGreaterThan(0);
  });

  it('does not offer lead-time editing to staff', async () => {
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder', user: staffUser });

    expect(await screen.findByRole('heading', { name: 'Reorder' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit lead time' })).not.toBeInTheDocument();
    expect(screen.getByText(/14-day supplier lead time/)).toBeInTheDocument();
  });

  it('says there is not enough history instead of inventing a suggestion', async () => {
    renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findAllByText('Pantry Coffee — Ethiopia Guji');

    const coffeeRow = rowForSku('COF-ETH-1KG');
    expect(within(coffeeRow).getByText('Not enough history')).toBeInTheDocument();
    expect(within(coffeeRow).getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<ReorderPage />, { route: '/stock/reorder' });
    await screen.findByRole('table', { name: /Reorder candidates/ });

    expect(await axe(container)).toHaveNoViolations();
  });
});
