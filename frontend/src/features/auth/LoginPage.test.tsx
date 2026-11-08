import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { LoginPage } from './LoginPage';
import { ApiError } from '../../lib/api/errors';
import { renderWithProviders } from '../../test/render';

function LoginRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/stock/products" element={<div>Products destination</div>} />
      <Route path="/orders/new" element={<div>Orders destination</div>} />
      <Route path="/account/password" element={<div>Password change destination</div>} />
    </Routes>
  );
}

describe('LoginPage', () => {
  it('fills the owner demo credentials', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginRoutes />, { route: '/login', user: null });

    await user.click(screen.getByRole('button', { name: 'Use owner demo' }));

    expect(screen.getByLabelText(/Email/)).toHaveValue('owner@stockledger.io');
    expect(screen.getByLabelText(/Password/)).toHaveValue('OwnerPass2026!');
  });

  it('submits credentials and navigates to the safe next route', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockResolvedValue({
      id: '00000000-0000-4000-8000-000000000001',
      email: 'owner@stockledger.io',
      fullName: 'Mara Ellison',
      role: 'OWNER',
      active: true,
      mustChangePassword: false,
    });
    renderWithProviders(<LoginRoutes />, {
      route: '/login?next=%2Forders%2Fnew',
      user: null,
      auth: { login },
    });

    await user.click(screen.getByRole('button', { name: 'Use owner demo' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(login).toHaveBeenCalledWith({
      email: 'owner@stockledger.io',
      password: 'OwnerPass2026!',
    });
    expect(await screen.findByText('Orders destination')).toBeInTheDocument();
  });

  it('shows the RFC 7807 detail when login is rejected', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockRejectedValue(
      new ApiError(401, {
        title: 'Unauthorized',
        status: 401,
        detail: 'The email or password is incorrect.',
        code: 'UNAUTHORIZED',
      }),
    );
    renderWithProviders(<LoginRoutes />, { route: '/login', user: null, auth: { login } });

    await user.click(screen.getByRole('button', { name: 'Use staff demo' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The email or password is incorrect.',
    );
  });

  it('redirects an authenticated user to the requested safe route', async () => {
    renderWithProviders(<LoginRoutes />, {
      route: '/login?next=%2Forders%2Fnew',
      user: {
        id: 'owner',
        email: 'owner@stockledger.io',
        fullName: 'Mara Ellison',
        role: 'OWNER',
        active: true,
        mustChangePassword: false,
      },
    });

    expect(await screen.findByText('Orders destination')).toBeInTheDocument();
  });

  it('routes a forced-change login directly to the password screen', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockResolvedValue({
      id: '00000000-0000-4000-8000-000000000002',
      email: 'staff@stockledger.io',
      fullName: 'Theo Marsh',
      role: 'STAFF',
      active: true,
      mustChangePassword: true,
    });
    renderWithProviders(<LoginRoutes />, {
      route: '/login?next=%2Fstock%2Fproducts',
      user: null,
      auth: { login },
    });

    await user.click(screen.getByRole('button', { name: 'Use staff demo' }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Password change destination')).toBeInTheDocument();
    expect(screen.queryByText('Products destination')).not.toBeInTheDocument();
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<LoginRoutes />, { route: '/login', user: null });
    expect(await axe(container)).toHaveNoViolations();
  });
});
