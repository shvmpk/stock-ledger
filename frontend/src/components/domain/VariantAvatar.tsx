import { useMemo } from 'react';
import { Avatar, Tooltip } from '@mantine/core';
import { skuTail, stringToColor, toInitials } from '../../lib/format/visual';

interface VariantAvatarProps {
  sku: string;
  optionValue?: string;
  size?: 'xs' | 'sm' | 'md';
  tooltip?: boolean;
}

export function VariantAvatar({
  sku,
  optionValue,
  size = 'sm',
  tooltip = true,
}: VariantAvatarProps) {
  const initials = useMemo(
    () => toInitials(optionValue?.trim() ? optionValue : skuTail(sku)),
    [sku, optionValue],
  );
  const background = useMemo(() => stringToColor(sku), [sku]);

  const avatar = (
    <Avatar
      size={size}
      radius="xl"
      variant="filled"
      color={background}
      autoContrast
      aria-hidden="true"
      data-testid="variant-avatar"
      data-sku={sku}
    >
      {initials}
    </Avatar>
  );

  if (!tooltip) return avatar;

  return (
    <Tooltip label={sku || 'Variant'} withArrow>
      {avatar}
    </Tooltip>
  );
}
