import { Link } from 'react-router-dom';
import { Button, Center, Group, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconArrowLeft, IconMapOff } from '@tabler/icons-react';

export function NotFoundPage() {
  return (
    <Center mih="70vh">
      <Stack align="center" gap="sm" maw={520} ta="center">
        <ThemeIcon variant="light" color="slate" size={48} radius="sm">
          <IconMapOff size={26} />
        </ThemeIcon>
        <Title order={1} size="h2">
          Page not found
        </Title>
        <Text c="dimmed">
          That route is not part of the operational ledger. Check the address or return to the stock
          catalog.
        </Text>
        <Group>
          <Button component={Link} to="/stock/products" leftSection={<IconArrowLeft size={16} />}>
            Back to stock
          </Button>
          <Button component={Link} to="/orders/new" variant="default">
            Open orders
          </Button>
        </Group>
      </Stack>
    </Center>
  );
}
