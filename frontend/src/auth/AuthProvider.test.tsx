import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { AUTH_STATE_CHANGED_KEY } from './crossTab';
import { useAuth } from './useAuth';
import { UNAUTHORIZED_EVENT } from '../lib/api/errors';
import { staffUser } from '../test/fixtures';
import { createTestQueryClient } from '../test/render';
import { server } from '../test/server';
import { theme } from '../theme/theme';

function AuthProbe() {
  const { status, user, login } = useAuth();
  return (
    <div>
      <p>
        {status}:{user?.fullName ?? 'none'}:{String(user?.mustChangePassword ?? false)}
      </p>
      <button
        type="button"
        onClick={() => void login({ email: 'staff@stockledger.io', password: 'StaffPass2026!' })}
      >
        Sign in as staff
      </button>
    </div>
  );
}

function renderAuth() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MantineProvider theme={theme} defaultColorScheme="light">
        <MemoryRouter>
          <AuthProvider>
            <AuthProbe />
          </AuthProvider>
        </MemoryRouter>
      </MantineProvider>
    </QueryClientProvider>,
  );
}

describe('AuthProvider', () => {
  it('bootstraps the current session and exposes the authenticated user', async () => {
    renderAuth();

    expect(screen.getByText('loading:none:false')).toBeInTheDocument();
    expect(await screen.findByText('authenticated:Mara Ellison:false')).toBeInTheDocument();
  });

  it('falls back to anonymous when the session endpoint returns 401', async () => {
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json(
          { title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
          { status: 401 },
        ),
      ),
    );

    renderAuth();

    expect(await screen.findByText('anonymous:none:false')).toBeInTheDocument();
  });

  it('logs in and updates the session context', async () => {
    const user = userEvent.setup();
    renderAuth();
    await screen.findByText('authenticated:Mara Ellison:false');

    await user.click(screen.getByRole('button', { name: 'Sign in as staff' }));

    expect(await screen.findByText('authenticated:Theo Marsh:false')).toBeInTheDocument();
  });

  it('refetches the session when another tab announces a password change', async () => {
    let meCalls = 0;
    server.use(
      http.get('/api/v1/auth/me', () => {
        meCalls += 1;
        return HttpResponse.json({
          success: true,
          data:
            meCalls === 1
              ? { ...staffUser, mustChangePassword: true }
              : { ...staffUser, mustChangePassword: false },
        });
      }),
    );

    renderAuth();
    expect(await screen.findByText('authenticated:Theo Marsh:true')).toBeInTheDocument();

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: AUTH_STATE_CHANGED_KEY,
          newValue: 'aaaabbbbccccdddd',
        }),
      );
    });

    expect(await screen.findByText('authenticated:Theo Marsh:false')).toBeInTheDocument();
    expect(meCalls).toBeGreaterThanOrEqual(2);
  });

  it('clears the session when the API broadcasts an unauthorized event', async () => {
    renderAuth();
    await screen.findByText('authenticated:Mara Ellison:false');

    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));

    await waitFor(() => expect(screen.getByText('anonymous:none:false')).toBeInTheDocument());
  });
});
