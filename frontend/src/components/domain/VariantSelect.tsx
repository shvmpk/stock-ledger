import { useMemo } from 'react';
import { Group, Select, Text } from '@mantine/core';
import type { Product, ProductVariant } from '../../lib/api/types';
import { VariantAvatar } from './VariantAvatar';

export function VariantSelect({
  products,
  value,
  onSelect,
  label = 'Variant',
  placeholder = 'Search by product, SKU, or barcode',
  error,
  required,
  disabled,
  excludeSkus = [],
}: {
  products: Product[];
  value: string | null;
  onSelect: (variant: ProductVariant | null) => void;
  label?: string;
  placeholder?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  excludeSkus?: string[];
}) {
  const { data, bySku } = useMemo(() => {
    const map = new Map<string, ProductVariant>();
    const options: Array<{ value: string; label: string }> = [];
    for (const product of products) {
      for (const variant of product.variants) {
        if (excludeSkus.includes(variant.sku)) continue;
        map.set(variant.sku, variant);
        options.push({
          value: variant.sku,
          label: `${product.name} — ${variant.optionValue} · ${variant.sku} · ${variant.available} available`,
        });
      }
    }
    options.sort((a, b) => a.label.localeCompare(b.label));
    return { data: options, bySku: map };
  }, [products, excludeSkus]);

  return (
    <Select
      label={label}
      placeholder={placeholder}
      data={data}
      value={value}
      onChange={(sku) => onSelect(sku ? bySku.get(sku) ?? null : null)}
      searchable
      clearable
      required={required}
      disabled={disabled}
      error={error}
      nothingFoundMessage="No matching variant"
      limit={60}
      comboboxProps={{ withinPortal: true }}
      renderOption={({ option }) => {
        const variant = bySku.get(option.value);
        if (!variant) return option.label;
        return (
          <Group gap="xs" wrap="nowrap" align="center">
            <VariantAvatar
              sku={variant.sku}
              optionValue={variant.optionValue}
              size="xs"
              tooltip={false}
            />
            <Text component="span" size="sm" style={{ minWidth: 0 }}>
              {option.label}
            </Text>
          </Group>
        );
      }}
    />
  );
}
