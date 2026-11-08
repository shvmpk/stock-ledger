import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Center, Loader, Stack, Text } from '@mantine/core';
import {
  PASSWORD_CHANGE_PATH,
  currentPathWithSearch,
  passwordChangePath,
} from '../lib/navigation/returnTo';
import { useAuth } from './useAuth';

export function FullPageLoader({ label = 'Checking your session…' }: { label?: string }) {
  return (
    <Center mih="60vh" role="status" aria-live="polite">
      <Stack align="center" gap="sm">
        <Loader color="emerald" />
        <Text size="sm" c="dimmed">
          {label}
        </Text>
      </Stack>
    </Center>
  );
}

export function RequireAuth() {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <FullPageLoader />;
  }

  if (status === 'anonymous') {
    const next = currentPathWithSearch(location.pathname, location.search);
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }

  if (user?.mustChangePassword && location.pathname !== PASSWORD_CHANGE_PATH) {
    const returnTo = currentPathWithSearch(location.pathname, location.search);
    return <Navigate to={passwordChangePath(returnTo)} replace />;
  }

  return <Outlet />;
}
