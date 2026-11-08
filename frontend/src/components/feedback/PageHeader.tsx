import type { ReactNode } from 'react';
import { Box, Group, Stack, Text, Title } from '@mantine/core';

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-start" gap="md" wrap="wrap" mb="lg">
      <Box>
        <Title order={1} size="h2">
          {title}
        </Title>
        {description ? (
          <Text c="dimmed" size="sm" mt={4} maw={760}>
            {description}
          </Text>
        ) : null}
      </Box>
      {actions ? (
        <Group gap="xs" wrap="wrap">
          {actions}
        </Group>
      ) : null}
    </Group>
  );
}

export function SectionHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-end" gap="md" mb="sm">
      <Stack gap={0}>
        <Title order={2} size="h4">
          {title}
        </Title>
        {description ? (
          <Text size="xs" c="dimmed">
            {description}
          </Text>
        ) : null}
      </Stack>
      {action}
    </Group>
  );
}
