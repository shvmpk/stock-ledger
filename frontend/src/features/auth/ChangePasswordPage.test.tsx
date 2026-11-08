import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { ChangePasswordPage } from './ChangePasswordPage';
import { AuthProvider } from '../../auth/AuthProvider';
import { RequireAuth } from '../../auth/RequireAuth';
import { staffUser } from '../../test/fixtures';
import { createTestQueryClient, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { theme } from '../../theme/theme';

const pendingStaff = { ...staffUser, mustChangePassword: true };
const newPassword = 'FjordLantern-2026!';

function ChangePasswordRoutes() {
  return (
    <Routes>
      <Route path="/account/password" element={<ChangePasswordPage />} />
      <Route path="/stock/products" element={<div>Products destination</div>} />
      <Route path="/stock/count" element={<div>Stock count destination</div>} />
    </Routes>
  );
}

function authProblem(
  status: number,
  title: string,
  detail: string,
  code: string,
  invalidParams: Array<{ field: string; message: string }> = [],
) {
  return HttpResponse.json(
    {
      type: `https://api.stockledger.io/errors/${code.toLowerCase().replaceAll('_', '-')}`,
      title,
      status,
      detail,
      code,
      timestamp: new Date().toISOString(),
      invalidParams,
    },
    { status },
  );
}

async function fillNewPassword(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^New password/), newPassword);
  await user.type(screen.getByLabelText(/^Confirm new password/), newPassword);
}

describe('ChangePasswordPage', () => {
  it('shows the forced-change copy, hides the temporary password field, and renders the live checklist', async () => {
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    expect(
      await screen.findByRole('heading', { name: 'Choose a new password' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Your store owner set a temporary password. Choose your own to continue.'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Current password/)).not.toBeInTheDocument();
    expect(screen.getByText('At least 12 characters')).toBeInTheDocument();
    expect(screen.getByText('Not a commonly used password')).toBeInTheDocument();
    expect(screen.getByText('Not based on your name or email')).toBeInTheDocument();
    expect(screen.getByText('Maximum 72 characters')).toBeInTheDocument();
  });

  it('always asks for the current password on a voluntary change', async () => {
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: staffUser,
    });

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument();
    expect(screen.getByLabelText(/^Current password/)).toBeInTheDocument();
  });

  it('uses password-manager-safe input attributes', async () => {
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    const newField = await screen.findByLabelText(/^New password/);
    const confirmField = screen.getByLabelText(/^Confirm new password/);
    for (const field of [newField, confirmField]) {
      expect(field).toHaveAttribute('autocomplete', 'new-password');
      expect(field).toHaveAttribute('autocapitalize', 'none');
      expect(field).toHaveAttribute('spellcheck', 'false');
    }
  });

  it('renders every backend policy violation under the new password field', async () => {
    server.use(
      http.post('/api/v1/auth/password', () =>
        authProblem(400, 'Weak Password', 'The new password is too weak.', 'WEAK_PASSWORD', [
          { field: 'newPassword', message: 'Password must be at least 12 characters.' },
          {
            field: 'newPassword',
            message: 'Password is too common. Choose a less predictable password.',
          },
        ]),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    await user.type(await screen.findByLabelText(/^New password/), 'short');
    await user.type(screen.getByLabelText(/^Confirm new password/), 'short');
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    expect(
      await screen.findByText('Password must be at least 12 characters.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Password is too common. Choose a less predictable password.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^New password/)).toHaveFocus();
  });

  it('validates the confirmation on blur and blocks submission locally', async () => {
    const changeRequest = vi.fn();
    server.use(
      http.post('/api/v1/auth/password', () => {
        changeRequest();
        return HttpResponse.json({
          success: true,
          data: { ...pendingStaff, mustChangePassword: false },
          meta: { timestamp: new Date().toISOString() },
        });
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    await user.type(await screen.findByLabelText(/^New password/), newPassword);
    const confirm = screen.getByLabelText(/^Confirm new password/);
    await user.type(confirm, `${newPassword}x`);
    expect(screen.queryByText('Passwords do not match.')).not.toBeInTheDocument();

    await user.tab();
    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Update password' }));
    expect(changeRequest).not.toHaveBeenCalled();
    expect(confirm).toHaveFocus();
  });

  it('clears the form, refreshes the session, and lands on returnTo after success', async () => {
    let resolveRefresh: () => void = () => undefined;
    const refresh = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveRefresh = resolve;
        }),
    );

    server.use(
      http.post('/api/v1/auth/password', () =>
        HttpResponse.json({
          success: true,
          data: { ...pendingStaff, mustChangePassword: false },
          meta: { timestamp: new Date().toISOString() },
        }),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password?returnTo=%2Fstock%2Fcount',
      user: pendingStaff,
      auth: { refresh },
    });

    await fillNewPassword(user);
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    await waitFor(() => expect(screen.getByLabelText(/^New password/)).toHaveValue(''));
    expect(refresh).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveRefresh();
    });
    expect(await screen.findByText('Stock count destination')).toBeInTheDocument();
  });

  it('treats a wrong current password as a field error rather than a logout', async () => {
    server.use(
      http.post('/api/v1/auth/password', () =>
        authProblem(
          400,
          'Invalid Current Password',
          'The current password is incorrect.',
          'INVALID_CURRENT_PASSWORD',
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: staffUser,
    });

    await user.type(await screen.findByLabelText(/^Current password/), 'WrongPassword2026!');
    await fillNewPassword(user);
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    expect(
      await screen.findByText("That password doesn't match your current one."),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Change password' })).toBeInTheDocument();
  });

  it('reveals the current password field when the forced-change window has elapsed', async () => {
    server.use(
      http.post('/api/v1/auth/password', () =>
        authProblem(
          400,
          'Current Password Required',
          'Enter your current password to continue.',
          'CURRENT_PASSWORD_REQUIRED',
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    expect(screen.queryByLabelText(/^Current password/)).not.toBeInTheDocument();
    await fillNewPassword(user);
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    const currentField = await screen.findByLabelText(/^Current password/);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'For your security, enter your temporary password to continue.',
    );
    await waitFor(() => expect(currentField).toHaveFocus());
  });

  it('returns an expired session to login with the change screen as next', async () => {
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json({ success: true, data: pendingStaff }),
      ),
      http.post('/api/v1/auth/password', () =>
        authProblem(401, 'Unauthorized', 'Authentication is required.', 'UNAUTHORIZED'),
      ),
    );

    function LocationDisplay() {
      const location = useLocation();
      return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
    }

    const user = userEvent.setup();
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <MantineProvider theme={theme} defaultColorScheme="light">
          <Notifications position="top-right" />
          <MemoryRouter initialEntries={['/account/password']}>
            <AuthProvider>
              <LocationDisplay />
              <Routes>
                <Route element={<RequireAuth />}>
                  <Route path="/account/password" element={<ChangePasswordPage />} />
                </Route>
                <Route path="/login" element={<div>Sign in destination</div>} />
              </Routes>
            </AuthProvider>
          </MemoryRouter>
        </MantineProvider>
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole('heading', { name: 'Choose a new password' }),
    ).toBeInTheDocument();

    await fillNewPassword(user);
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    expect(await screen.findByText('Sign in destination')).toBeInTheDocument();
    const location = new URL(screen.getByTestId('location').textContent ?? '', 'http://local');
    expect(location.pathname).toBe('/login');
    expect(location.searchParams.get('next')).toBe('/account/password');
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<ChangePasswordRoutes />, {
      route: '/account/password',
      user: pendingStaff,
    });

    expect(await axe(container)).toHaveNoViolations();
  });
});
