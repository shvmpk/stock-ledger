import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { VariantSelect } from './VariantSelect';
import { products } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';

describe('VariantSelect', () => {
  it('renders a variant avatar inside each dropdown option', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <VariantSelect products={products} value={null} onSelect={vi.fn()} />,
    );

    const input = await screen.findByPlaceholderText('Search by product, SKU, or barcode');
    await user.click(input);
    await user.type(input, 'HOOD-NVY-XL');

    const optionLabel = await screen.findByText(
      /Heavyweight Cotton Hoodie — XL · HOOD-NVY-XL/,
    );
    const option = optionLabel.closest('[role="option"]');
    expect(option).not.toBeNull();
    const avatar = within(option as HTMLElement).getByTestId('variant-avatar');
    expect(avatar).toHaveAttribute('data-sku', 'HOOD-NVY-XL');
    expect(avatar).toHaveTextContent('XL');
  });
});
