import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { ShippingRatesPage } from './ShippingRatesPage';
import { renderWithProviders } from '../../test/render';

describe('ShippingRatesPage', () => {
  it('edits a carrier and parcel-size rate in minor units', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ShippingRatesPage />, { route: '/settings/rates' });

    expect(await screen.findByRole('heading', { name: 'Shipping rates' })).toBeInTheDocument();
    await screen.findByRole('table', { name: /Carrier × parcel-size matrix/ });
    expect(screen.getByLabelText('Shipping rate summary')).toHaveTextContent('11 / 12');
    expect(screen.getByLabelText('Shipping rate summary')).toHaveTextContent('£6.23');

    await user.click(screen.getAllByRole('button', { name: 'Edit rate' })[0]);
    const modal = await screen.findByRole('dialog', { name: /DHL Express · Small/ });
    const cost = within(modal).getByLabelText(/Cost \(GBP\)/);
    await user.clear(cost);
    await user.type(cost, '4.50');
    await user.click(within(modal).getByRole('button', { name: 'Save rate' }));

    expect(await screen.findByText('Shipping rate saved')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getAllByText('£4.50').length).toBeGreaterThan(0);
  });

  it('rejects an invalid cost without saving', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ShippingRatesPage />, { route: '/settings/rates' });
    await screen.findByRole('table', { name: /Carrier × parcel-size matrix/ });

    await user.click(screen.getAllByRole('button', { name: 'Edit rate' })[0]);
    const modal = await screen.findByRole('dialog', { name: /DHL Express · Small/ });
    const cost = within(modal).getByLabelText(/Cost \(GBP\)/);
    await user.clear(cost);
    await user.type(cost, '4.505');
    await user.click(within(modal).getByRole('button', { name: 'Save rate' }));

    expect(
      await within(modal).findByText('Enter a valid non-negative amount with up to two decimals.'),
    ).toBeInTheDocument();
  });

  it('deactivates a rate so dispatch no longer resolves it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ShippingRatesPage />, { route: '/settings/rates' });
    await screen.findByRole('table', { name: /Carrier × parcel-size matrix/ });

    await user.click(screen.getAllByRole('button', { name: 'Edit rate' })[0]);
    const modal = await screen.findByRole('dialog', { name: /DHL Express · Small/ });
    await user.click(within(modal).getByRole('switch', { name: /Active for dispatch/ }));
    await user.click(within(modal).getByRole('button', { name: 'Save rate' }));

    expect(await screen.findByText('Shipping rate saved')).toBeInTheDocument();
    expect(
      await screen.findByText(/DHL Express Small is inactive at £4.20/),
    ).toBeInTheDocument();
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<ShippingRatesPage />, { route: '/settings/rates' });
    await screen.findByRole('table', { name: /Carrier × parcel-size matrix/ });
    expect(await axe(container)).toHaveNoViolations();
  });
});
