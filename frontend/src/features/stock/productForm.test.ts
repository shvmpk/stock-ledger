import { describe, expect, it } from 'vitest';
import {
  emptyVariantDraft,
  mapProductFormErrors,
  toProductInput,
  validateProductForm,
  type ProductFormValues,
} from './productForm';
import { ApiError } from '../../lib/api/errors';

function validValues(): ProductFormValues {
  return {
    name: 'Merino Wool Crew Socks',
    category: 'Accessories',
    description: 'Fine-gauge merino knit, reinforced heel and toe.',
    variants: [
      {
        ...emptyVariantDraft('first'),
        sku: 'sock-mrn-m',
        barcode: '5060123456718',
        optionName: 'Size',
        optionValue: 'M',
        costPrice: '4.50',
        sellingPrice: '14.00',
        weightGrams: 80,
        minStockThreshold: 10,
      },
    ],
  };
}

describe('productForm', () => {
  it('accepts a complete product', () => {
    expect(validateProductForm(validValues())).toEqual({});
  });

  it('requires a name and at least one variant', () => {
    const errors = validateProductForm({
      ...validValues(),
      name: '   ',
      variants: [],
    });

    expect(errors.name).toBe('Product name is required.');
    expect(errors.variants).toBe('Add at least one variant.');
  });

  it('requires a SKU and a valid retail price for every variant', () => {
    const values = validValues();
    values.variants[0] = {
      ...values.variants[0],
      sku: '  ',
      sellingPrice: 'fourteen pounds',
    };

    const errors = validateProductForm(values);

    expect(errors['variants.0.sku']).toBe('SKU is required.');
    expect(errors['variants.0.sellingPrice']).toBe(
      'Enter a retail price with up to two decimal places.',
    );
  });

  it('refuses negative cost, weight and reorder level', () => {
    const values = validValues();
    values.variants[0] = {
      ...values.variants[0],
      costPrice: '-1.00',
      weightGrams: -80,
      minStockThreshold: -10,
    };

    const errors = validateProductForm(values);

    expect(errors['variants.0.costPrice']).toBe('Cost cannot be negative.');
    expect(errors['variants.0.weightGrams']).toBe('Weight cannot be negative.');
    expect(errors['variants.0.minStockThreshold']).toBe('Reorder level cannot be negative.');
  });

  it('shapes the API payload with normalised SKUs and optional blanks removed', () => {
    const values = validValues();
    values.variants[0] = {
      ...values.variants[0],
      sku: '  sock-mrn-m  ',
      barcode: '  ',
      optionName: '  ',
      optionValue: '  ',
      costPrice: '  ',
      weightGrams: null,
      minStockThreshold: null,
    };

    const payload = toProductInput(values);

    expect(payload.name).toBe('Merino Wool Crew Socks');
    expect(payload.variants[0]).toEqual({
      sku: 'SOCK-MRN-M',
      sellingPrice: '14.00',
      costPrice: undefined,
      barcode: undefined,
      optionName: undefined,
      optionValue: undefined,
      weightGrams: undefined,
      minStockThreshold: undefined,
      id: undefined,
    });
  });

  it('keeps existing variant ids so edits update instead of duplicate', () => {
    const values = validValues();
    values.variants[0] = { ...values.variants[0], id: '00000000-0000-4000-8000-000000001011' };

    const payload = toProductInput(values);

    expect(payload.variants[0].id).toBe('00000000-0000-4000-8000-000000001011');
  });

  it('maps field-level API errors back to the form', () => {
    const error = new ApiError(400, {
      title: 'Validation Failed',
      status: 400,
      detail: 'One or more product fields are invalid.',
      code: 'VALIDATION_FAILED',
      invalidParams: [
        {
          field: 'variants[1].sku',
          message: "SKU 'SOCK-MRN-M' is already in use. Choose a different SKU.",
        },
        { field: 'name', message: 'Product name is required.' },
      ],
    });

    const errors = mapProductFormErrors(error);

    expect(errors['variants.1.sku']).toContain('SOCK-MRN-M');
    expect(errors.name).toBe('Product name is required.');
  });
});
