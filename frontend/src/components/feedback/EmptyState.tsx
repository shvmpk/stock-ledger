import type { ReactNode } from 'react';
import { Center, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconInbox } from '@tabler/icons-react';

export function EmptyState({
  title,
  description,
  icon,
  action,
}: {
  title: string;
  description: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Center py="xl">
      <Stack align="center" gap="xs" maw={480} ta="center">
        <ThemeIcon variant="light" size={40} radius="sm" color="slate" aria-hidden="true">
          {icon ?? <IconInbox size={22} />}
        </ThemeIcon>
        <Title order={2} size="h4">
          {title}
        </Title>
        <Text size="sm" c="dimmed">
          {description}
        </Text>
        {action}
      </Stack>
    </Center>
  );
}
