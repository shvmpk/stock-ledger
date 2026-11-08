import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { CategoryIcon } from './CategoryIcon';
import { renderWithProviders } from '../../test/render';
import { clearIconCache } from '../../lib/format/categoryIcons';
import { hashString } from '../../lib/format/visual';

describe('CategoryIcon', () => {
  beforeEach(() => {
    clearIconCache();
  });

  it('renders the resolved Tabler icon and hue for a known category', () => {
    const { container } = renderWithProviders(
      <CategoryIcon category="Apparel" withBackground />,
    );

    const wrapper = screen.getByTestId('category-icon');
    const icon = container.querySelector('svg');

    expect(wrapper).toHaveAttribute('data-category', 'Apparel');
    expect(wrapper).toHaveAttribute('data-hue', '220');
    expect(wrapper).toHaveAttribute('aria-hidden', 'true');
    expect(icon).toHaveClass('tabler-icon', 'tabler-icon-hanger');
    expect(wrapper).not.toHaveTextContent('Apparel');
  });

  it('shows the category label when requested', () => {
    const { container } = renderWithProviders(
      <CategoryIcon category="Coffee & Tea" withLabel />,
    );

    expect(screen.getByText('Coffee & Tea')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveClass('tabler-icon-coffee');
    expect(screen.getByTestId('category-icon')).not.toHaveAttribute('aria-hidden');
  });

  it('renders a friendly label for a blank category', () => {
    const { container } = renderWithProviders(<CategoryIcon category="   " withLabel />);

    expect(screen.getByText('Uncategorised')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveClass('tabler-icon-tag');
    expect(screen.getByTestId('category-icon')).toHaveAttribute('data-hue', '210');
  });

  it('renders an unknown category through the deterministic fallback pool', () => {
    const { container } = renderWithProviders(
      <CategoryIcon category="Vintage Ceramics" withBackground />,
    );

    const wrapper = screen.getByTestId('category-icon');
    const expectedHue = String(hashString('vintage ceramics') % 360);
    expect(wrapper).toHaveAttribute('data-hue', expectedHue);
    expect(container.querySelector('svg')).toHaveClass('tabler-icon');
    expect(container.querySelector('svg')).toBeInTheDocument();
  });
});
