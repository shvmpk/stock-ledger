import { useMemo } from 'react';
import {
  Box,
  Group,
  Text,
  ThemeIcon,
  useComputedColorScheme,
} from '@mantine/core';
import { hslPairFromHue } from '../../lib/format/visual';
import { resolveIcon } from '../../lib/format/categoryIcons';

interface CategoryIconProps {
  category: string;
  size?: number;
  withLabel?: boolean;
  withBackground?: boolean;
}

export function CategoryIcon({
  category,
  size = 18,
  withLabel = false,
  withBackground = false,
}: CategoryIconProps) {
  const resolved = useMemo(() => resolveIcon(category), [category]);
  const scheme = useComputedColorScheme('light', { getInitialValueInEffect: false });
  const label = category.trim() || 'Uncategorised';
  const pair = hslPairFromHue(resolved.hue);
  const iconColor =
    scheme === 'dark'
      ? `hsl(${resolved.hue}, 70%, 70%)`
      : `hsl(${resolved.hue}, 65%, 34%)`;

  const Icon = resolved.icon;
  const icon = <Icon size={size} aria-hidden="true" />;

  return (
    <Group
      gap={6}
      wrap="nowrap"
      align="center"
      aria-hidden={withLabel ? undefined : true}
      data-testid="category-icon"
      data-category={category}
      data-hue={resolved.hue}
    >
      {withBackground ? (
        <ThemeIcon
          size={size + 10}
          radius="xl"
          variant="transparent"
          style={{
            backgroundColor: pair.bg,
            color: pair.color,
            flex: '0 0 auto',
          }}
        >
          {icon}
        </ThemeIcon>
      ) : (
        <Box
          component="span"
          style={{ color: iconColor, display: 'inline-flex', flex: '0 0 auto' }}
        >
          {icon}
        </Box>
      )}
      {withLabel ? (
        <Text component="span" size="sm">
          {label}
        </Text>
      ) : null}
    </Group>
  );
}
