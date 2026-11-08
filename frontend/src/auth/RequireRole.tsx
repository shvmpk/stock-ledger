import { Outlet } from 'react-router-dom';
import { Center, Stack, Text, Title } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';
import { useAuth } from './useAuth';
import type { Role } from '../lib/api/types';

export function ForbiddenPage() {
  return (
    <Center mih="55vh">
      <Stack align="center" gap="xs" maw={440} ta="center">
        <IconLock size={32} aria-hidden="true" color="var(--mantine-color-dimmed)" />
        <Title order={1} size="h2">
          Owner access required
        </Title>
        <Text c="dimmed">
          This area contains financial or administrative data. Ask the store owner if you need access.
        </Text>
      </Stack>
    </Center>
  );
}

export function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) {
    return <ForbiddenPage />;
  }
  return <Outlet />;
}
