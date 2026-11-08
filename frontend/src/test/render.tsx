import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../auth/context';
import type { User } from '../lib/api/types';
import { theme } from '../theme/theme';
import { ownerUser, staffUser } from './fixtures';

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

export interface ProviderOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string;
  user?: User | null;
  status?: AuthStatus;
  auth?: Partial<AuthContextValue>;
  queryClient?: QueryClient;
}

export interface ProviderRenderResult extends RenderResult {
  queryClient: QueryClient;
  user: User | null;
}

export function renderWithProviders(
  ui: ReactElement,
  {
    route = '/',
    user = ownerUser,
    status = user ? 'authenticated' : 'anonymous',
    auth,
    queryClient = createTestQueryClient(),
    ...options
  }: ProviderOptions = {},
): ProviderRenderResult {
  const authValue: AuthContextValue = {
    user,
    status,
    login: vi.fn(async (input) =>
      input.email === staffUser.email ? staffUser : ownerUser,
    ),
    logout: vi.fn(async () => undefined),
    refresh: vi.fn(async () => undefined),
    ...auth,
  };

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MantineProvider theme={theme} defaultColorScheme="light">
        <Notifications position="top-right" />
        <AuthContext.Provider value={authValue}>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </AuthContext.Provider>
      </MantineProvider>
    </QueryClientProvider>
  );

  return { ...render(ui, { wrapper, ...options }), queryClient, user };
}

export { ownerUser, staffUser };
