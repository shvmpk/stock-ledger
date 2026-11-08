import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VariantAvatar } from './VariantAvatar';
import { renderWithProviders } from '../../test/render';
import { stringToColor } from '../../lib/format/visual';

describe('VariantAvatar', () => {
  it('derives initials and a deterministic background from the SKU and option value', () => {
    renderWithProviders(<VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" />);

    const avatar = screen.getByTestId('variant-avatar');
    expect(avatar).toHaveTextContent('XL');
    expect(avatar).toHaveAttribute('data-sku', 'HOOD-NVY-XL');
    expect(avatar).toHaveAttribute('aria-hidden', 'true');
    expect(avatar.style.getPropertyValue('--avatar-bg')).toBe(stringToColor('HOOD-NVY-XL'));
  });

  it('uses the last meaningful SKU segment when no option value is supplied', () => {
    renderWithProviders(<VariantAvatar sku="HOOD-NVY-XL" />);
    expect(screen.getByTestId('variant-avatar')).toHaveTextContent('XL');

    renderWithProviders(<VariantAvatar sku="COF-ETH-250G" />);
    expect(screen.getAllByTestId('variant-avatar')[1]).toHaveTextContent('25');
  });

  it('supports a single-character option value', () => {
    renderWithProviders(<VariantAvatar sku="HOOD-NVY-M" optionValue="M" />);
    expect(screen.getByTestId('variant-avatar')).toHaveTextContent('M');
  });

  it('maps the requested sizes to Mantine avatar sizes', () => {
    renderWithProviders(
      <>
        <VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" size="xs" />
        <VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" size="sm" />
        <VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" size="md" />
      </>,
    );

    const avatars = screen.getAllByTestId('variant-avatar');
    expect(avatars[0].style.getPropertyValue('--avatar-size')).toBe('var(--avatar-size-xs)');
    expect(avatars[1].style.getPropertyValue('--avatar-size')).toBe('var(--avatar-size-sm)');
    expect(avatars[2].style.getPropertyValue('--avatar-size')).toBe('var(--avatar-size-md)');
  });

  it('shows the full SKU in a tooltip by default', async () => {
    const user = userEvent.setup();
    renderWithProviders(<VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" />);

    await user.hover(screen.getByTestId('variant-avatar'));

    expect(await screen.findByRole('tooltip')).toHaveTextContent('HOOD-NVY-XL');
  });

  it('can omit the tooltip when the SKU is already visible elsewhere', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <VariantAvatar sku="HOOD-NVY-XL" optionValue="XL" tooltip={false} />,
    );

    await user.hover(screen.getByTestId('variant-avatar'));

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });
});
