import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { UserMenu } from './UserMenu';
import { renderWithProviders } from '../../test/render';

function MenuRoutes() {
  return (
    <Routes>
      <Route path="/account/password" element={<div>Change password destination</div>} />
      <Route path="*" element={<UserMenu />} />
    </Routes>
  );
}

describe('UserMenu', () => {
  it('offers Change password above Sign out and navigates to the account screen', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MenuRoutes />, { route: '/stock/products' });

    await user.click(screen.getByRole('button', { name: 'Open user menu' }));
    const menu = await screen.findByRole('menu', { hidden: true });
    const items = within(menu).getAllByRole('menuitem', { hidden: true });
    const labels = items.map((item) => item.textContent ?? '');
    const changeIndex = labels.findIndex((label) => label.includes('Change password'));
    const signOutIndex = labels.findIndex((label) => label.includes('Sign out'));

    expect(changeIndex).toBeGreaterThanOrEqual(0);
    expect(signOutIndex).toBeGreaterThan(changeIndex);

    fireEvent.click(
      within(menu).getByRole('menuitem', { name: /Change password/, hidden: true }),
    );

    expect(await screen.findByText('Change password destination')).toBeInTheDocument();
  });
});
