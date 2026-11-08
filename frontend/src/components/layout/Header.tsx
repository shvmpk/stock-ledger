import { Link } from 'react-router-dom';
import { Box, Burger, Group, Text, Tooltip } from '@mantine/core';
import { ColorSchemeToggle } from './ColorSchemeToggle';
import { UserMenu } from './UserMenu';

export function Header({
  mobileOpened,
  onToggleMobile,
}: {
  mobileOpened: boolean;
  onToggleMobile: () => void;
}) {
  return (
    <Group h="100%" px="md" justify="space-between" wrap="nowrap">
      <Group gap="sm" wrap="nowrap">
        <Burger
          opened={mobileOpened}
          onClick={onToggleMobile}
          hiddenFrom="md"
          size="sm"
          aria-label={mobileOpened ? 'Close navigation' : 'Open navigation'}
        />
        <Box
          component={Link}
          to="/stock/products"
          aria-label="StockLedger home"
          style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}
        >
          <Box
            aria-hidden="true"
            style={{
              width: 30,
              height: 30,
              borderRadius: 7,
              background: 'var(--mantine-color-emerald-7)',
              color: 'white',
              display: 'grid',
              placeItems: 'center',
              fontSize: 11,
              fontWeight: 800,
              letterSpacing: '0.02em',
            }}
          >
            SL
          </Box>
          <Box visibleFrom="xs">
            <Text fw={750} size="sm" lh={1.1} c="var(--mantine-color-text)">
              StockLedger
            </Text>
            <Text size="10px" c="dimmed" lh={1.2} tt="uppercase" style={{ letterSpacing: '0.08em' }}>
              Inventory &amp; order ledger
            </Text>
          </Box>
        </Box>
      </Group>
      <Group gap={4} wrap="nowrap">
        <Tooltip label="Toggle colour scheme" withArrow>
          <Box>
            <ColorSchemeToggle />
          </Box>
        </Tooltip>
        <UserMenu />
      </Group>
    </Group>
  );
}
