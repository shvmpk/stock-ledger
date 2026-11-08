import type { ReactNode } from 'react';
import { Group } from '@mantine/core';

export function FilterBar({ children }: { children: ReactNode }) {
  return (
    <Group align="flex-end" gap="sm" wrap="wrap" mb="md">
      {children}
    </Group>
  );
}
