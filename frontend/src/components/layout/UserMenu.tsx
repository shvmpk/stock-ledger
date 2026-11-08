import { useNavigate } from 'react-router-dom';
import { Avatar, Group, Menu, Stack, Text, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconKey, IconLogout, IconUserCircle } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <Menu position="bottom-end" width={240} shadow="md" withinPortal>
      <Menu.Target>
        <UnstyledButton aria-label="Open user menu">
          <Group gap="xs" wrap="nowrap">
            <Avatar color="emerald" radius="xl" size="sm">
              {initials(user.fullName)}
            </Avatar>
            <Stack gap={0} visibleFrom="sm">
              <Text size="xs" fw={600} lh={1.1}>
                {user.fullName}
              </Text>
              <Text size="10px" c="dimmed" tt="uppercase" style={{ letterSpacing: '0.06em' }}>
                {user.role === 'OWNER' ? 'Owner' : 'Staff'}
              </Text>
            </Stack>
            <IconChevronDown size={14} aria-hidden="true" />
          </Group>
        </UnstyledButton>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>Signed in</Menu.Label>
        <Menu.Item leftSection={<IconUserCircle size={16} />} disabled>
          <Stack gap={0}>
            <Text size="sm">{user.fullName}</Text>
            <Text size="xs" c="dimmed">
              {user.email}
            </Text>
          </Stack>
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item
          leftSection={<IconKey size={16} />}
          onClick={() => navigate('/account/password')}
        >
          Change password
        </Menu.Item>
        <Menu.Item color="red" leftSection={<IconLogout size={16} />} onClick={() => void handleLogout()}>
          Sign out
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
