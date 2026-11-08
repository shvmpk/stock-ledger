import { ActionIcon, useMantineColorScheme } from '@mantine/core';
import { IconMoon, IconSun } from '@tabler/icons-react';

export function ColorSchemeToggle() {
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const dark = colorScheme === 'dark';
  return (
    <ActionIcon
      variant="subtle"
      color="gray"
      size="lg"
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      onClick={() => setColorScheme(dark ? 'light' : 'dark')}
    >
      {dark ? <IconSun size={18} /> : <IconMoon size={18} />}
    </ActionIcon>
  );
}
