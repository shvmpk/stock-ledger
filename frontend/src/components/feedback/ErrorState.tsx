import { Center, Stack, Text, Title } from '@mantine/core';
import { IconCloudOff } from '@tabler/icons-react';
import { ProblemAlert } from './ProblemAlert';

export function ErrorState({
  error,
  title = 'This view could not load',
  onRetry,
}: {
  error: unknown;
  title?: string;
  onRetry?: () => void;
}) {
  return (
    <Center py="xl">
      <Stack gap="sm" maw={620} w="100%">
        <Stack gap={2} align="center" ta="center">
          <IconCloudOff size={30} aria-hidden="true" color="var(--mantine-color-dimmed)" />
          <Title order={2} size="h4">
            {title}
          </Title>
          <Text size="sm" c="dimmed">
            The data is safe. Check the connection and retry.
          </Text>
        </Stack>
        <ProblemAlert error={error} onRetry={onRetry} />
      </Stack>
    </Center>
  );
}
