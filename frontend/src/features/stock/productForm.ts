import { isApiError } from '../../lib/api/errors';
import { parseMoneyInput } from '../../lib/format/money';
import type { ProductInput, ProductVariantInput } from '../../lib/api/types';

export interface VariantDraft {
  key: string;
  id?: string;
  sku: string;
  barcode: string;
  optionName: string;
  optionValue: string;
  costPrice: string;
  sellingPrice: string;
  weightGrams: number | null;
  minStockThreshold: number | null;
}

export interface ProductFormValues {
  name: string;
  category: string;
  description: string;
  variants: VariantDraft[];
}

export function emptyVariantDraft(key: string): VariantDraft {
  return {
    key,
    sku: '',
    barcode: '',
    optionName: '',
    optionValue: '',
    costPrice: '',
    sellingPrice: '',
    weightGrams: null,
    minStockThreshold: null,
  };
}

export function validateProductForm(values: ProductFormValues): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!values.name.trim()) {
    errors.name = 'Product name is required.';
  } else if (values.name.trim().length > 255) {
    errors.name = 'Product name must be 255 characters or fewer.';
  }

  if (values.category.trim().length > 100) {
    errors.category = 'Category must be 100 characters or fewer.';
  }

  if (values.description.trim().length > 5000) {
    errors.description = 'Description must be 5000 characters or fewer.';
  }

  if (values.variants.length === 0) {
    errors.variants = 'Add at least one variant.';
    return errors;
  }

  values.variants.forEach((variant, index) => {
    const prefix = `variants.${index}`;
    const sku = variant.sku.trim();

    if (!sku) {
      errors[`${prefix}.sku`] = 'SKU is required.';
    } else if (sku.length > 100) {
      errors[`${prefix}.sku`] = 'SKU must be 100 characters or fewer.';
    }

    if (variant.sellingPrice.trim() === '') {
      errors[`${prefix}.sellingPrice`] = 'Retail price is required.';
    } else if (Number(variant.sellingPrice) < 0) {
      errors[`${prefix}.sellingPrice`] = 'Retail price cannot be negative.';
    } else if (parseMoneyInput(variant.sellingPrice) === null) {
      errors[`${prefix}.sellingPrice`] = 'Enter a retail price with up to two decimal places.';
    }

    if (variant.costPrice.trim() !== '') {
      if (Number(variant.costPrice) < 0) {
        errors[`${prefix}.costPrice`] = 'Cost cannot be negative.';
      } else if (parseMoneyInput(variant.costPrice) === null) {
        errors[`${prefix}.costPrice`] = 'Enter a cost with up to two decimal places.';
      }
    }

    if (variant.weightGrams !== null && variant.weightGrams < 0) {
      errors[`${prefix}.weightGrams`] = 'Weight cannot be negative.';
    }

    if (variant.minStockThreshold !== null && variant.minStockThreshold < 0) {
      errors[`${prefix}.minStockThreshold`] = 'Reorder level cannot be negative.';
    }

    if (variant.barcode.trim().length > 100) {
      errors[`${prefix}.barcode`] = 'Barcode must be 100 characters or fewer.';
    }

    if (variant.optionName.trim().length > 100) {
      errors[`${prefix}.optionName`] = 'Option name must be 100 characters or fewer.';
    }

    if (variant.optionValue.trim().length > 100) {
      errors[`${prefix}.optionValue`] = 'Option value must be 100 characters or fewer.';
    }
  });

  return errors;
}

export function toProductInput(values: ProductFormValues): ProductInput {
  return {
    name: values.name.trim(),
    description: trimOrUndefined(values.description),
    category: trimOrUndefined(values.category),
    variants: values.variants.map(toVariantInput),
  };
}

function toVariantInput(variant: VariantDraft): ProductVariantInput {
  return {
    id: variant.id,
    sku: variant.sku.trim().toUpperCase(),
    barcode: trimOrUndefined(variant.barcode),
    optionName: trimOrUndefined(variant.optionName),
    optionValue: trimOrUndefined(variant.optionValue),
    costPrice:
      variant.costPrice.trim() === ''
        ? undefined
        : (parseMoneyInput(variant.costPrice) ?? undefined),
    sellingPrice: parseMoneyInput(variant.sellingPrice) ?? variant.sellingPrice.trim(),
    weightGrams: variant.weightGrams ?? undefined,
    minStockThreshold: variant.minStockThreshold ?? undefined,
  };
}

export function mapProductFormErrors(error: unknown): Record<string, string> {
  if (!isApiError(error)) return {};

  const errors: Record<string, string> = {};
  for (const param of error.invalidParams) {
    const variantMatch = /^variants\[(\d+)]\.(\w+)$/.exec(param.field);
    const key = variantMatch ? `variants.${variantMatch[1]}.${variantMatch[2]}` : param.field;
    errors[key] = param.message;
  }
  return errors;
}

function trimOrUndefined(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}
