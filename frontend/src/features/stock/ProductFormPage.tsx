import { useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Grid,
  Group,
  Loader,
  NumberInput,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconArrowLeft, IconPlus, IconTrash } from '@tabler/icons-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createProduct,
  getProduct,
  updateProduct,
} from '../../lib/api/endpoints/products';
import type { Product, ProductInput } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import {
  emptyVariantDraft,
  mapProductFormErrors,
  toProductInput,
  validateProductForm,
  type ProductFormValues,
  type VariantDraft,
} from './productForm';

let draftSequence = 0;

function nextDraftKey(): string {
  draftSequence += 1;
  return `variant-${draftSequence}`;
}

function blankValues(): ProductFormValues {
  return {
    name: '',
    category: '',
    description: '',
    variants: [emptyVariantDraft(nextDraftKey())],
  };
}

function valuesFromProduct(product: Product): ProductFormValues {
  return {
    name: product.name,
    category: product.category ?? '',
    description: product.description ?? '',
    variants: product.variants.map((variant) => ({
      key: variant.id,
      id: variant.id,
      sku: variant.sku,
      barcode: variant.barcode ?? '',
      optionName: variant.optionName ?? '',
      optionValue: variant.optionValue ?? '',
      costPrice:
        variant.costPrice === null || variant.costPrice === undefined
          ? ''
          : String(variant.costPrice),
      sellingPrice: String(variant.sellingPrice),
      weightGrams: variant.weightGrams,
      minStockThreshold: variant.minStockThreshold,
    })),
  };
}

export function ProductFormPage() {
  const { productId } = useParams<{ productId: string }>();
  const isEdit = Boolean(productId);

  const productQuery = useQuery({
    queryKey: queryKeys.product(productId ?? 'new'),
    queryFn: () => getProduct(productId as string),
    enabled: isEdit,
  });

  if (isEdit && productQuery.isError) {
    return (
      <>
        <PageHeader title="Edit product" description="Update the catalogue entry and its variants." />
        <ErrorState error={productQuery.error} onRetry={() => void productQuery.refetch()} />
      </>
    );
  }

  if (isEdit && productQuery.isLoading) {
    return (
      <Group>
        <Loader size="sm" />
        <Text size="sm" c="dimmed">
          Loading the product…
        </Text>
      </Group>
    );
  }

  return (
    <ProductForm
      key={productQuery.data?.id ?? 'new'}
      isEdit={isEdit}
      productId={productId}
      product={productQuery.data}
    />
  );
}

function ProductForm({
  isEdit,
  productId,
  product,
}: {
  isEdit: boolean;
  productId?: string;
  product?: Product;
}) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [values, setValues] = useState<ProductFormValues>(() =>
    product ? valuesFromProduct(product) : blankValues(),
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);

  const mutation = useMutation({
    mutationFn: (input: ProductInput) =>
      isEdit ? updateProduct(productId as string, input) : createProduct(input),
    onSuccess: async (product) => {
      queryClient.setQueryData(queryKeys.product(product.id), product);
      await queryClient.invalidateQueries({ queryKey: queryKeys.products });
      notifications.show({
        color: 'green',
        title: isEdit ? 'Product updated' : 'Product added',
        message: isEdit
          ? `${product.name} has been updated.`
          : `${product.name} is in the catalogue. Receive stock when the delivery arrives.`,
      });
      navigate('/stock/products');
    },
    onError: (caught) => {
      const mapped = mapProductFormErrors(caught);
      setFieldErrors(mapped);
      setError(Object.keys(mapped).length > 0 ? null : caught);
    },
  });

  const updateField = (field: 'name' | 'category' | 'description', value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const updateVariant = (index: number, patch: Partial<VariantDraft>) => {
    setValues((current) => ({
      ...current,
      variants: current.variants.map((variant, position) =>
        position === index ? { ...variant, ...patch } : variant,
      ),
    }));
    setFieldErrors((current) => {
      const prefix = `variants.${index}.`;
      const next = { ...current };
      for (const key of Object.keys(next)) {
        if (key.startsWith(prefix)) delete next[key];
      }
      return next;
    });
  };

  const addVariant = () => {
    setValues((current) => ({
      ...current,
      variants: [...current.variants, emptyVariantDraft(nextDraftKey())],
    }));
    setFieldErrors((current) => {
      if (!current.variants) return current;
      const next = { ...current };
      delete next.variants;
      return next;
    });
  };

  const removeVariant = (index: number) => {
    setValues((current) => ({
      ...current,
      variants: current.variants.filter((_, position) => position !== index),
    }));
    setFieldErrors({});
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (mutation.isPending) return;

    const errors = validateProductForm(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError(null);
      return;
    }
    setError(null);
    mutation.mutate(toProductInput(values));
  };

  return (
    <>
      <PageHeader
        title={isEdit ? 'Edit product' : 'Add product'}
        description={
          isEdit
            ? 'Change the product details or variant prices. Past orders and stock movements keep the details they were recorded with.'
            : 'Create the catalogue entry, then receive stock when the delivery arrives.'
        }
        actions={
          <Button
            variant="default"
            component={Link}
            to="/stock/products"
            leftSection={<IconArrowLeft size={16} />}
          >
            Back to stock
          </Button>
        }
      />

      <Card withBorder padding="lg" radius="md">
        <form onSubmit={handleSubmit}>
          <Stack gap="xl">
            <Box>
              <Title order={2} size="h5" mb="sm">
                Product details
              </Title>
              <Grid gutter="md">
                <Grid.Col span={{ base: 12, md: 7 }}>
                  <TextInput
                    label="Product name"
                    description="What you call it in the shop, for example “Merino Wool Crew Socks”."
                    placeholder="Merino Wool Crew Socks"
                    withAsterisk
                    aria-required="true"
                    value={values.name}
                    onChange={(event) => updateField('name', event.currentTarget.value)}
                    error={fieldErrors.name}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 5 }}>
                  <TextInput
                    label="Category"
                    description="Optional. Groups products in filters, for example “Accessories”."
                    placeholder="Accessories"
                    value={values.category}
                    onChange={(event) => updateField('category', event.currentTarget.value)}
                    error={fieldErrors.category}
                  />
                </Grid.Col>
                <Grid.Col span={12}>
                  <Textarea
                    label="Description"
                    description="Optional. Materials, sizing notes, or anything your team needs to know."
                    placeholder="Fine-gauge merino knit, reinforced heel and toe."
                    autosize
                    minRows={2}
                    maxRows={5}
                    value={values.description}
                    onChange={(event) => updateField('description', event.currentTarget.value)}
                    error={fieldErrors.description}
                  />
                </Grid.Col>
              </Grid>
            </Box>

            <Box>
              <Group justify="space-between" align="flex-start" mb="sm">
                <Box>
                  <Title order={2} size="h5">
                    Variants
                  </Title>
                  <Text size="sm" c="dimmed">
                    Every size, colour, or pack size you sell separately needs its own SKU and stock
                    record.
                  </Text>
                </Box>
                <Button
                  type="button"
                  variant="light"
                  leftSection={<IconPlus size={16} />}
                  onClick={addVariant}
                >
                  Add variant
                </Button>
              </Group>

              {fieldErrors.variants ? (
                <Alert
                  color="red"
                  variant="light"
                  icon={<IconAlertTriangle size={18} />}
                  mb="md"
                  role="alert"
                >
                  {fieldErrors.variants}
                </Alert>
              ) : null}

              <Stack gap="md">
                {values.variants.map((variant, index) => (
                  <VariantFields
                    key={variant.key}
                    index={index}
                    variant={variant}
                    errors={fieldErrors}
                    canRemove={!variant.id}
                    onChange={(patch) => updateVariant(index, patch)}
                    onRemove={() => removeVariant(index)}
                  />
                ))}
              </Stack>

              {values.variants.length === 0 ? (
                <Text size="sm" c="dimmed" mt="sm">
                  Add at least one variant to continue.
                </Text>
              ) : null}
            </Box>

            {error ? (
              <ProblemAlert error={error} title="Could not save the product" compact />
            ) : null}

            <Group justify="flex-end">
              <Button type="button" variant="default" component={Link} to="/stock/products">
                Cancel
              </Button>
              <Button type="submit" loading={mutation.isPending}>
                {isEdit ? 'Save changes' : 'Add product'}
              </Button>
            </Group>
          </Stack>
        </form>
      </Card>
    </>
  );
}

function VariantFields({
  index,
  variant,
  errors,
  canRemove,
  onChange,
  onRemove,
}: {
  index: number;
  variant: VariantDraft;
  errors: Record<string, string>;
  canRemove: boolean;
  onChange: (patch: Partial<VariantDraft>) => void;
  onRemove: () => void;
}) {
  const prefix = `variants.${index}`;

  return (
    <Card withBorder padding="md" radius="md">
      <Group justify="space-between" align="center" mb="sm">
        <Text fw={650}>Variant {index + 1}</Text>
        {canRemove ? (
          <Button
            type="button"
            variant="subtle"
            color="red"
            size="compact-sm"
            leftSection={<IconTrash size={14} />}
            aria-label={`Remove variant ${index + 1}`}
            onClick={onRemove}
          >
            Remove
          </Button>
        ) : (
          <Text size="xs" c="dimmed">
            Existing variant
          </Text>
        )}
      </Group>

      <Grid gutter="md">
        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <TextInput
            label="SKU"
            description="Your unique stock code."
            placeholder="SOCK-MRN-M"
            withAsterisk
            aria-required="true"
            value={variant.sku}
            onChange={(event) => onChange({ sku: event.currentTarget.value })}
            error={errors[`${prefix}.sku`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <TextInput
            label="Barcode"
            description="Optional. EAN-13 or your own label."
            placeholder="5060123456718"
            value={variant.barcode}
            onChange={(event) => onChange({ barcode: event.currentTarget.value })}
            error={errors[`${prefix}.barcode`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <TextInput
            label="Option name"
            description="Optional. For example Size."
            placeholder="Size"
            value={variant.optionName}
            onChange={(event) => onChange({ optionName: event.currentTarget.value })}
            error={errors[`${prefix}.optionName`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <TextInput
            label="Option value"
            description="Optional. For example M."
            placeholder="M"
            value={variant.optionValue}
            onChange={(event) => onChange({ optionValue: event.currentTarget.value })}
            error={errors[`${prefix}.optionValue`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <TextInput
            label="Retail price (GBP)"
            description="What the customer pays."
            placeholder="14.00"
            withAsterisk
            aria-required="true"
            value={variant.sellingPrice}
            onChange={(event) => onChange({ sellingPrice: event.currentTarget.value })}
            error={errors[`${prefix}.sellingPrice`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <TextInput
            label="Cost price (GBP)"
            description="Leave blank if you don’t know it yet."
            placeholder="4.50"
            value={variant.costPrice}
            onChange={(event) => onChange({ costPrice: event.currentTarget.value })}
            error={errors[`${prefix}.costPrice`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <NumberInput
            label="Weight (grams)"
            description="Leave blank if you don’t know it yet."
            placeholder="80"
            min={0}
            allowDecimal={false}
            value={variant.weightGrams ?? ''}
            onChange={(value) =>
              onChange({ weightGrams: typeof value === 'number' ? value : null })
            }
            error={errors[`${prefix}.weightGrams`]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, md: 3 }}>
          <NumberInput
            label="Reorder level"
            description="Leave blank for no low-stock warning."
            placeholder="5"
            min={0}
            allowDecimal={false}
            value={variant.minStockThreshold ?? ''}
            onChange={(value) =>
              onChange({ minStockThreshold: typeof value === 'number' ? value : null })
            }
            error={errors[`${prefix}.minStockThreshold`]}
          />
        </Grid.Col>
      </Grid>
    </Card>
  );
}
