import { useCallback, useEffect, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  type LoginInput,
} from '../lib/api/endpoints/auth';
import { UNAUTHORIZED_EVENT } from '../lib/api/errors';
import type { User } from '../lib/api/types';
import { queryKeys } from '../lib/query/keys';
import { AuthContext, type AuthContextValue, type AuthStatus } from './context';
import { subscribeToAuthStateChanged } from './crossTab';

function removeSessionQueries(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.removeQueries({
    predicate: (query) => query.queryKey[0] !== queryKeys.me[0],
  });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const meQuery = useQuery<User | null>({
    queryKey: queryKeys.me,
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  const user = meQuery.data ?? null;
  const status: AuthStatus = meQuery.isPending
    ? 'loading'
    : user
      ? 'authenticated'
      : 'anonymous';

  useEffect(() => {
    const handleUnauthorized = () => {
      queryClient.setQueryData<User | null>(queryKeys.me, null);
      removeSessionQueries(queryClient);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [queryClient]);

  useEffect(
    () =>
      subscribeToAuthStateChanged(() => {
        void queryClient.invalidateQueries({ queryKey: queryKeys.me });
      }),
    [queryClient],
  );

  const login = useCallback(
    async (input: LoginInput) => {
      const authenticated = await loginRequest(input);
      queryClient.setQueryData<User | null>(queryKeys.me, authenticated);
      return authenticated;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } finally {
      queryClient.setQueryData<User | null>(queryKeys.me, null);
      removeSessionQueries(queryClient);
    }
  }, [queryClient]);

  const refresh = useCallback(async () => {
    try {
      const current = await fetchCurrentUser();
      queryClient.setQueryData<User | null>(queryKeys.me, current);
    } catch (caught) {
      queryClient.setQueryData<User | null>(queryKeys.me, null);
      throw caught;
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, login, logout, refresh }),
    [user, status, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
