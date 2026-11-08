import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RequireAuth } from './RequireAuth';
import { staffUser } from '../test/fixtures';
import { renderWithProviders } from '../test/render';

function GuardedRoutes() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route
          path="/stock/products"
          element={<div data-testid="app-shell">Protected inventory</div>}
        />
        <Route path="/account/password" element={<h1>Choose a new password</h1>} />
      </Route>
    </Routes>
  );
}

describe('RequireAuth', () => {
  it('redirects a forced-change user to the change screen before any protected child renders', async () => {
    renderWithProviders(<GuardedRoutes />, {
      route: '/stock/products',
      user: { ...staffUser, mustChangePassword: true },
    });

    expect(
      await screen.findByRole('heading', { name: 'Choose a new password' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('app-shell')).not.toBeInTheDocument();
  });

  it('renders the normal protected route when the flag has been refreshed to false', async () => {
    renderWithProviders(<GuardedRoutes />, {
      route: '/stock/products',
      user: { ...staffUser, mustChangePassword: false },
    });

    expect(await screen.findByTestId('app-shell')).toHaveTextContent('Protected inventory');
  });

  it('does not redirect the change screen back onto itself', async () => {
    renderWithProviders(<GuardedRoutes />, {
      route: '/account/password',
      user: { ...staffUser, mustChangePassword: true },
    });

    expect(
      await screen.findByRole('heading', { name: 'Choose a new password' }),
    ).toBeInTheDocument();
  });
});
