import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Card,
  Group,
  Menu,
  Modal,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  Tooltip,
} from '@mantine/core';
import {
  IconArchive,
  IconArchiveOff,
  IconChevronDown,
  IconChevronRight,
  IconClipboardList,
  IconDots,
  IconHelpCircle,
  IconLayoutGrid,
  IconPackageImport,
  IconPencil,
  IconPlus,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listProducts,
  restoreProduct,
  restoreVariant,
  retireProduct,
  retireVariant,
} from '../../lib/api/endpoints/products';
import type { Product, ProductVariant } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { useAuth } from '../../auth/useAuth';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { TableSkeleton } from '../../components/feedback/TableSkeleton';
import { SearchInput } from '../../components/data/SearchInput';
import { FilterBar } from '../../components/data/FilterBar';
import { TablePagination } from '../../components/data/TablePagination';
import { CopyableCode } from '../../components/data/CopyableCode';
import { CategoryIcon } from '../../components/domain/CategoryIcon';
import { LedgerEquation, LedgerNumbers } from '../../components/domain/LedgerCells';
import { VariantAvatar } from '../../components/domain/VariantAvatar';
import { StockStatusBadge } from '../../components/data/StatusBadge';
import { Money } from '../../components/data/Money';
import { formatMargin } from '../../lib/format/money';
import { formatQuantity } from '../../lib/format/quantity';
import { errorMessage } from '../../lib/api/errors';
import { ReceiveStockDrawer } from './ReceiveStockDrawer';
import { StockExplanationDrawer } from './StockExplanationDrawer';

const PAGE_SIZE = 50;

type SortKey = 'name' | 'sku' | 'stock';

interface VariantEntry {
  product: Product;
  variant: ProductVariant;
}

interface RetireTarget {
  product: Product;
  variant?: ProductVariant;
}

function matchesSearch(entry: VariantEntry, query: string): boolean {
  const haystack = [
    entry.product.name,
    entry.product.category,
    entry.variant.sku,
    entry.variant.barcode,
    entry.variant.optionName,
    entry.variant.optionValue,
  ]
    .join(' ')
    .toLowerCase();
  return haystack.includes(query);
}

function sortEntries(entries: VariantEntry[], sort: SortKey): VariantEntry[] {
  return [...entries].sort((a, b) => {
    if (sort === 'sku') return a.variant.sku.localeCompare(b.variant.sku);
    if (sort === 'stock') {
      return a.variant.available - b.variant.available || a.variant.sku.localeCompare(b.variant.sku);
    }
    return (
      a.product.name.localeCompare(b.product.name) ||
      a.variant.optionValue.localeCompare(b.variant.optionValue)
    );
  });
}

function aggregate(product: Product) {
  return product.variants.reduce(
    (totals, variant) => ({
      onHand: totals.onHand + variant.onHand,
      reserved: totals.reserved + variant.reserved,
      available: totals.available + variant.available,
      low:
        totals.low +
        (variant.minStockThreshold !== null && variant.available <= variant.minStockThreshold
          ? 1
          : 0),
    }),
    { onHand: 0, reserved: 0, available: 0, low: 0 },
  );
}

function findVariantEntry(products: Product[], sku: string): VariantEntry | undefined {
  for (const product of products) {
    const variant = product.variants.find((entry) => entry.sku === sku);
    if (variant) return { product, variant };
  }
  return undefined;
}

function RetireMenu({
  label,
  active,
  scope,
  onSelect,
  iconSize = 15,
}: {
  label: string;
  active: boolean;
  scope: 'product' | 'variant';
  onSelect: () => void;
  iconSize?: number;
}) {
  const Icon = active ? IconArchive : IconArchiveOff;
  return (
    <Menu shadow="md" width={200} position="bottom-end">
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label={`Actions for ${label}`}
        >
          <IconDots size={iconSize} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item leftSection={<Icon size={14} />} onClick={onSelect}>
          {active ? 'Retire' : 'Restore'} {scope}
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}

export function AllProductsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const isOwner = user?.role === 'OWNER';
  const [includeRetired, setIncludeRetired] = useState(searchParams.get('retired') === '1');
  const productsQuery = useQuery({
    queryKey: includeRetired ? queryKeys.retiredProducts : queryKeys.products,
    queryFn: () => listProducts(includeRetired),
  });
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string>('all');
  const [sort, setSort] = useState<SortKey>('name');
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [receiveTarget, setReceiveTarget] = useState<{
    variantId?: string;
    variantSku?: string;
    variantLabel?: string;
  } | null>(null);
  const [explainVariantId, setExplainVariantId] = useState<string | null>(null);
  const [retireTarget, setRetireTarget] = useState<RetireTarget | null>(null);

  const retireMutation = useMutation({
    mutationFn: async (target: RetireTarget) => {
      if (target.variant) {
        return target.variant.active
          ? retireVariant(target.product.id, target.variant.id)
          : restoreVariant(target.product.id, target.variant.id);
      }
      return target.product.active
        ? retireProduct(target.product.id)
        : restoreProduct(target.product.id);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.products });
      setRetireTarget(null);
    },
  });

  const products = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);
  const receiveSkuParam = searchParams.get('receiveSku');
  const explainSkuParam = searchParams.get('explainSku');

  const activeReceiveTarget = useMemo(() => {
    if (!receiveSkuParam) return receiveTarget;
    if (!productsQuery.isSuccess) return null;
    const match = findVariantEntry(products, receiveSkuParam);
    return match
      ? {
          variantId: match.variant.id,
          variantSku: match.variant.sku,
          variantLabel: `${match.product.name} — ${match.variant.optionValue} · ${match.variant.sku}`,
        }
      : { variantSku: receiveSkuParam };
  }, [products, productsQuery.isSuccess, receiveSkuParam, receiveTarget]);

  const activeExplainVariantId = useMemo(() => {
    if (!explainSkuParam) return explainVariantId;
    return findVariantEntry(products, explainSkuParam)?.variant.id ?? null;
  }, [explainSkuParam, explainVariantId, products]);

  const closeReceive = () => {
    setReceiveTarget(null);
    if (receiveSkuParam) {
      const next = new URLSearchParams(searchParams);
      next.delete('receiveSku');
      setSearchParams(next, { replace: true });
    }
  };

  const closeExplanation = () => {
    setExplainVariantId(null);
    if (explainSkuParam) {
      const next = new URLSearchParams(searchParams);
      next.delete('explainSku');
      setSearchParams(next, { replace: true });
    }
  };

  const retireVerb = retireTarget
    && (retireTarget.variant ? !retireTarget.variant.active : !retireTarget.product.active)
    ? 'Restore'
    : 'Retire';
  const retireSubject = retireTarget?.variant?.sku ?? retireTarget?.product.name ?? '';

  const categories = useMemo(
    () => Array.from(new Set(products.map((product) => product.category))).sort(),
    [products],
  );

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return sortEntries(
      products
        .flatMap((product) => product.variants.map((variant) => ({ product, variant })))
        .filter((entry) => (category === 'all' ? true : entry.product.category === category))
        .filter((entry) => (query ? matchesSearch(entry, query) : true)),
      sort,
    );
  }, [products, category, search, sort]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const visible = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const grouped = useMemo(() => {
    const map = new Map<string, { product: Product; variants: ProductVariant[] }>();
    for (const entry of visible) {
      const existing = map.get(entry.product.id);
      if (existing) {
        existing.variants.push(entry.variant);
      } else {
        map.set(entry.product.id, { product: entry.product, variants: [entry.variant] });
      }
    }
    return Array.from(map.values());
  }, [visible]);

  const summary = useMemo(() => {
    const variants = products.flatMap((product) => product.variants);
    return {
      products: products.length,
      variants: variants.length,
      onHand: variants.reduce((sum, variant) => sum + variant.onHand, 0),
      low: variants.filter(
        (variant) =>
          variant.minStockThreshold !== null &&
          variant.available > 0 &&
          variant.available <= variant.minStockThreshold,
      ).length,
      out: variants.filter((variant) => variant.available <= 0).length,
    };
  }, [products]);

  const allVisibleExpanded =
    grouped.length > 0 && grouped.every((group) => expanded.has(group.product.id));

  const toggleProduct = (productId: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  const toggleAllVisible = () => {
    setExpanded((current) => {
      const next = new Set(current);
      if (allVisibleExpanded) {
        grouped.forEach((group) => next.delete(group.product.id));
      } else {
        grouped.forEach((group) => next.add(group.product.id));
      }
      return next;
    });
  };

  const openReceive = (variant: ProductVariant, product: Product) => {
    setReceiveTarget({
      variantId: variant.id,
      variantSku: variant.sku,
      variantLabel: `${product.name} — ${variant.optionValue} · ${variant.sku}`,
    });
  };

  const setRetiredVisible = (next: boolean) => {
    setIncludeRetired(next);
    setPage(0);
    const params = new URLSearchParams(searchParams);
    if (next) params.set('retired', '1');
    else params.delete('retired');
    setSearchParams(params, { replace: true });
  };

  const goToCount = (variant: ProductVariant) => {
    navigate(`/stock/count?sku=${encodeURIComponent(variant.sku)}`);
  };

  if (productsQuery.isError) {
    return (
      <>
        <PageHeader title="Stock" description="Every variant, with the ledger equation visible." />
        <ErrorState error={productsQuery.error} onRetry={() => void productsQuery.refetch()} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Stock"
        description="Physical truth, reserved commitments, and sellable availability for every variant."
        actions={
          <>
            {isOwner ? (
              <Button
                component={Link}
                to="/stock/products/new"
                variant="light"
                leftSection={<IconPlus size={16} />}
              >
                Add product
              </Button>
            ) : null}
            <Button
              variant="default"
              leftSection={<IconClipboardList size={16} />}
              onClick={() => navigate('/stock/count')}
            >
              Stock count
            </Button>
            <Button
              leftSection={<IconPackageImport size={16} />}
              onClick={() => setReceiveTarget({})}
            >
              Receive stock
            </Button>
          </>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="Catalog summary">
        <SummaryItem label="Products" value={summary.products} />
        <SummaryItem label="Variants" value={summary.variants} />
        <SummaryItem label="Units on hand" value={summary.onHand} />
        <SummaryItem label="Low stock" value={summary.low} />
        <SummaryItem label="Out of stock" value={summary.out} />
      </Box>

      <FilterBar>
        <Box style={{ flex: '1 1 280px', maxWidth: 420 }}>
          <SearchInput
            value={search}
            onChange={(value) => {
              setSearch(value);
              setPage(0);
            }}
            placeholder="Search product, SKU, barcode, or option"
            label="Search catalog"
          />
        </Box>
        <Select
          label="Category"
          value={category}
          onChange={(value) => {
            setCategory(value ?? 'all');
            setPage(0);
          }}
          data={[{ value: 'all', label: 'All categories' }, ...categories.map((item) => ({ value: item, label: item }))]}
          w={200}
          allowDeselect={false}
          renderOption={({ option }) =>
            option.value === 'all' ? (
              <Group gap="xs" wrap="nowrap">
                <IconLayoutGrid size={16} aria-hidden="true" />
                <Text component="span" size="sm">
                  {option.label}
                </Text>
              </Group>
            ) : (
              <CategoryIcon category={option.value} withLabel />
            )
          }
        />
        <Select
          label="Sort"
          value={sort}
          onChange={(value) => {
            setSort((value as SortKey) ?? 'name');
            setPage(0);
          }}
          data={[
            { value: 'name', label: 'Product name' },
            { value: 'sku', label: 'SKU' },
            { value: 'stock', label: 'Available: low first' },
          ]}
          w={190}
          allowDeselect={false}
        />
        <Switch
          label="Show retired"
          checked={includeRetired}
          onChange={(event) => setRetiredVisible(event.currentTarget.checked)}
        />
        <Button variant="subtle" onClick={toggleAllVisible} disabled={grouped.length === 0}>
          {allVisibleExpanded ? 'Collapse all' : 'Expand all'}
        </Button>
      </FilterBar>

      {productsQuery.isLoading ? (
        <TableSkeleton rows={8} columns={8} />
      ) : products.length === 0 ? (
        <EmptyState
          title="The catalog is empty"
          description="Add your products or connect an online store to start tracking stock."
          action={
            isOwner ? (
              <Button
                component={Link}
                to="/stock/products/new"
                leftSection={<IconPlus size={16} />}
              >
                Add product
              </Button>
            ) : undefined
          }
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="No variants match"
          description="Try a different product name, SKU, barcode, or category."
          action={
            <Button
              variant="light"
              onClick={() => {
                setSearch('');
                setCategory('all');
                setPage(0);
              }}
            >
              Clear filters
            </Button>
          }
        />
      ) : (
        <>
          <Box visibleFrom="md">
            <Table className="sl-data-table" captionSide="top">
              <Table.Caption className="sl-table-caption">
                {formatQuantity(filtered.length)} variants in the filtered catalog. Expand a product to
                inspect its ledger.
              </Table.Caption>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th scope="col">Product / variant</Table.Th>
                  <Table.Th scope="col">Category</Table.Th>
                  <Table.Th scope="col" ta="right">
                    Cost
                  </Table.Th>
                  <Table.Th scope="col" ta="right">
                    Retail
                  </Table.Th>
                  <Table.Th scope="col" ta="right">
                    Margin
                  </Table.Th>
                  <Table.Th scope="col" ta="right">
                    <Stack gap={0} align="flex-end">
                      <Text span size="sm" fw={600}>
                        Ledger
                      </Text>
                      <Text span className="sl-ledger-caption">
                        Available = On hand − Reserved
                      </Text>
                    </Stack>
                  </Table.Th>
                  <Table.Th scope="col">Status</Table.Th>
                  <Table.Th scope="col" ta="right">
                    Actions
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {grouped.map(({ product, variants }) => {
                  const isExpanded = expanded.has(product.id);
                  const totals = aggregate(product);
                  return (
                    <ProductRows
                      key={product.id}
                      product={product}
                      visibleVariants={variants}
                      totals={totals}
                      expanded={isExpanded}
                      onToggle={() => toggleProduct(product.id)}
                      onReceive={openReceive}
                      onCount={goToCount}
                      onExplain={(variant) => setExplainVariantId(variant.id)}
                      onRetire={(target) => {
                        retireMutation.reset();
                        setRetireTarget(target);
                      }}
                      canEdit={isOwner}
                    />
                  );
                })}
              </Table.Tbody>
            </Table>
          </Box>

          <Stack hiddenFrom="md" gap="sm" aria-label="Catalog variants">
            {visible.map(({ product, variant }) => {
              const sellable = product.active && variant.active;
              return (
                <Card key={variant.id} withBorder padding="sm" radius="md">
                  <Stack gap="xs">
                    <Group justify="space-between" align="flex-start" wrap="nowrap">
                      <VariantAvatar sku={variant.sku} optionValue={variant.optionValue} size="sm" />
                      <Box style={{ flex: 1, minWidth: 0 }}>
                        <Group gap={6} wrap="wrap">
                          <Text fw={650} size="sm">
                            {product.name}
                          </Text>
                          {!product.active ? (
                            <Badge color="gray" variant="light" size="xs">
                              Retired
                            </Badge>
                          ) : null}
                        </Group>
                        <Group gap={6} wrap="wrap">
                          <Text size="xs" c="dimmed">
                            {variant.optionName}: {variant.optionValue}
                          </Text>
                          {!variant.active ? (
                            <Badge color="gray" variant="light" size="xs">
                              Retired
                            </Badge>
                          ) : null}
                        </Group>
                        <Box mt={4}>
                          <CategoryIcon
                            category={product.category}
                            size={13}
                            withLabel
                            withBackground
                          />
                        </Box>
                      </Box>
                      <StockStatusBadge available={variant.available} threshold={variant.minStockThreshold} />
                    </Group>
                    <Group gap="md" wrap="wrap">
                      <CopyableCode value={variant.sku} label="SKU" />
                      <CopyableCode value={variant.barcode} label="Barcode" />
                    </Group>
                    <Group gap="lg">
                      <Text size="xs" c="dimmed">
                        Cost <Money value={variant.costPrice} />
                      </Text>
                      <Text size="xs" c="dimmed">
                        Retail <Money value={variant.sellingPrice} />
                      </Text>
                      <Text size="xs" c="dimmed">
                        Margin {formatMargin(variant.costPrice, variant.sellingPrice)}
                      </Text>
                    </Group>
                    <LedgerEquation
                      onHand={variant.onHand}
                      reserved={variant.reserved}
                      available={variant.available}
                    />
                    <Group gap="xs">
                      {sellable ? (
                        <>
                          <Button
                            size="xs"
                            variant="light"
                            leftSection={<IconPackageImport size={14} />}
                            onClick={() => openReceive(variant, product)}
                          >
                            Receive
                          </Button>
                          <Button
                            size="xs"
                            variant="subtle"
                            leftSection={<IconClipboardList size={14} />}
                            onClick={() => goToCount(variant)}
                          >
                            Count
                          </Button>
                        </>
                      ) : null}
                      <Button
                        size="xs"
                        variant="subtle"
                        leftSection={<IconHelpCircle size={14} />}
                        onClick={() => setExplainVariantId(variant.id)}
                      >
                        Why this number?
                      </Button>
                      {isOwner ? (
                        <Button
                          size="xs"
                          variant="subtle"
                          component={Link}
                          to={`/stock/products/${product.id}/edit`}
                          leftSection={<IconPencil size={14} />}
                        >
                          Edit product
                        </Button>
                      ) : null}
                      {isOwner ? (
                        <RetireMenu
                          label={product.name}
                          active={product.active}
                          scope="product"
                          iconSize={16}
                          onSelect={() => {
                            retireMutation.reset();
                            setRetireTarget({ product });
                          }}
                        />
                      ) : null}
                      {isOwner && product.active ? (
                        <RetireMenu
                          label={variant.sku}
                          active={variant.active}
                          scope="variant"
                          iconSize={16}
                          onSelect={() => {
                            retireMutation.reset();
                            setRetireTarget({ product, variant });
                          }}
                        />
                      ) : null}
                    </Group>
                  </Stack>
                </Card>
              );
            })}
          </Stack>

          <TablePagination
            page={safePage}
            totalPages={totalPages}
            totalElements={filtered.length}
            size={PAGE_SIZE}
            onChange={setPage}
          />
        </>
      )}

      {activeReceiveTarget ? (
        <ReceiveStockDrawer
          key={
            activeReceiveTarget.variantId ??
            activeReceiveTarget.variantSku ??
            'catalog'
          }
          opened
          onClose={closeReceive}
          variantId={activeReceiveTarget.variantId}
          variantSku={activeReceiveTarget.variantSku}
          variantLabel={activeReceiveTarget.variantLabel}
        />
      ) : null}

      {activeExplainVariantId ? (
        <StockExplanationDrawer
          variantId={activeExplainVariantId}
          onClose={closeExplanation}
        />
      ) : null}

      <Modal
        opened={retireTarget !== null}
        onClose={() => {
          if (!retireMutation.isPending) {
            setRetireTarget(null);
            retireMutation.reset();
          }
        }}
        title={`${retireVerb} ${retireSubject}?`}
        centered
      >
        <Stack gap="md">
          <Text size="sm">
            {retireVerb === 'Retire'
              ? 'It will disappear from the working catalogue and cannot be ordered or received. Its past orders and stock movements stay visible.'
              : 'It will return to the working catalogue and can be ordered and received again.'}
          </Text>
          {retireMutation.isError ? (
            <Text size="sm" c="red">
              {errorMessage(retireMutation.error)}
            </Text>
          ) : null}
          <Group justify="flex-end">
            <Button
              variant="default"
              disabled={retireMutation.isPending}
              onClick={() => {
                setRetireTarget(null);
                retireMutation.reset();
              }}
            >
              Cancel
            </Button>
            <Button
              color={retireVerb === 'Retire' ? 'red' : 'green'}
              loading={retireMutation.isPending}
              onClick={() => {
                if (retireTarget) retireMutation.mutate(retireTarget);
              }}
            >
              {retireVerb} {retireTarget?.variant ? 'variant' : 'product'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

function SummaryItem({ label, value }: { label: string; value: number }) {
  return (
    <Box className="sl-summary-item">
      <Text className="sl-summary-value">{formatQuantity(value)}</Text>
      <Text className="sl-summary-label">{label}</Text>
    </Box>
  );
}

function ProductRows({
  product,
  visibleVariants,
  totals,
  expanded,
  canEdit,
  onToggle,
  onReceive,
  onCount,
  onExplain,
  onRetire,
}: {
  product: Product;
  visibleVariants: ProductVariant[];
  totals: { onHand: number; reserved: number; available: number; low: number };
  expanded: boolean;
  canEdit: boolean;
  onToggle: () => void;
  onReceive: (variant: ProductVariant, product: Product) => void;
  onCount: (variant: ProductVariant) => void;
  onExplain: (variant: ProductVariant) => void;
  onRetire: (target: RetireTarget) => void;
}) {
  return (
    <>
      <Table.Tr>
        <Table.Th scope="row" fw={600}>
          <Group gap="xs" wrap="nowrap">
            <Tooltip label={expanded ? 'Collapse variants' : 'Expand variants'} withArrow>
              <ActionIcon
                variant="subtle"
                color="gray"
                size="sm"
                aria-label={`${expanded ? 'Collapse' : 'Expand'} ${product.name}`}
                aria-expanded={expanded}
                onClick={onToggle}
              >
                {expanded ? <IconChevronDown size={15} /> : <IconChevronRight size={15} />}
              </ActionIcon>
            </Tooltip>
            <CategoryIcon category={product.category} size={16} withBackground />
            <Text size="sm" fw={650}>
              {product.name}
            </Text>
            <Badge variant="light" color="slate" size="xs">
              {product.variants.length} variants
            </Badge>
            {!product.active ? (
              <Badge variant="light" color="gray" size="xs">
                Retired
              </Badge>
            ) : null}
          </Group>
        </Table.Th>
        <Table.Td>
          <Text size="xs">{product.category}</Text>
        </Table.Td>
        <Table.Td ta="right">—</Table.Td>
        <Table.Td ta="right">—</Table.Td>
        <Table.Td ta="right">—</Table.Td>
        <Table.Td>
          <Stack gap={0} align="flex-end">
            <Group gap="md" justify="flex-end">
              <Text size="sm" className="sl-tabular">
                {formatQuantity(totals.onHand)}
              </Text>
              <Text size="sm" className="sl-tabular">
                {formatQuantity(totals.reserved)}
              </Text>
              <Text size="sm" fw={700} className="sl-tabular">
                {formatQuantity(totals.available)}
              </Text>
            </Group>
            <Text className="sl-ledger-caption">On hand · Reserved · Available</Text>
          </Stack>
        </Table.Td>
        <Table.Td>
          {totals.low > 0 ? (
            <Badge color="amber" variant="light" size="xs">
              {totals.low} low
            </Badge>
          ) : (
            <Text size="xs" c="dimmed">
              —
            </Text>
          )}
        </Table.Td>
        <Table.Td ta="right">
          <Group gap={4} justify="flex-end" wrap="nowrap">
            {canEdit ? (
              <Tooltip label="Edit product" withArrow>
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  size="sm"
                  component={Link}
                  to={`/stock/products/${product.id}/edit`}
                  aria-label={`Edit ${product.name}`}
                >
                  <IconPencil size={14} />
                </ActionIcon>
              </Tooltip>
            ) : null}
            {canEdit ? (
              <RetireMenu
                label={product.name}
                active={product.active}
                scope="product"
                onSelect={() => onRetire({ product })}
              />
            ) : null}
            <Button variant="subtle" size="compact-xs" onClick={onToggle}>
              {expanded ? 'Hide' : 'Show'} variants
            </Button>
          </Group>
        </Table.Td>
      </Table.Tr>

      {expanded
        ? visibleVariants.map((variant) => {
            const sellable = product.active && variant.active;
            return (
              <Table.Tr key={variant.id}>
                <Table.Th scope="row" fw={400} pl={44}>
                  <Group gap="sm" wrap="nowrap" align="flex-start">
                    <VariantAvatar sku={variant.sku} optionValue={variant.optionValue} size="sm" />
                    <Stack gap={2}>
                      <Group gap={6} wrap="wrap">
                        <Text size="sm">{variant.optionValue}</Text>
                        {!variant.active ? (
                          <Badge color="gray" variant="light" size="xs">
                            Retired
                          </Badge>
                        ) : null}
                      </Group>
                      <Text size="xs" c="dimmed">
                        {variant.optionName}
                      </Text>
                      <Group gap="sm" wrap="wrap">
                        <CopyableCode value={variant.sku} label="SKU" />
                        <CopyableCode value={variant.barcode} label="Barcode" />
                      </Group>
                    </Stack>
                  </Group>
                </Table.Th>
                <Table.Td />
                <Table.Td ta="right">
                  <Money value={variant.costPrice} />
                </Table.Td>
                <Table.Td ta="right">
                  <Money value={variant.sellingPrice} />
                </Table.Td>
                <Table.Td ta="right">
                  <Text size="sm" className="sl-tabular">
                    {formatMargin(variant.costPrice, variant.sellingPrice)}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <LedgerNumbers
                    onHand={variant.onHand}
                    reserved={variant.reserved}
                    available={variant.available}
                    threshold={variant.minStockThreshold}
                  />
                </Table.Td>
                <Table.Td>
                  <StockStatusBadge available={variant.available} threshold={variant.minStockThreshold} />
                </Table.Td>
                <Table.Td ta="right">
                  <Group gap={4} justify="flex-end" wrap="nowrap">
                    {sellable ? (
                      <>
                        <Button
                          size="compact-xs"
                          variant="subtle"
                          leftSection={<IconPackageImport size={13} />}
                          onClick={() => onReceive(variant, product)}
                        >
                          Receive
                        </Button>
                        <Button
                          size="compact-xs"
                          variant="subtle"
                          leftSection={<IconClipboardList size={13} />}
                          onClick={() => onCount(variant)}
                        >
                          Count
                        </Button>
                      </>
                    ) : null}
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      leftSection={<IconHelpCircle size={13} />}
                      onClick={() => onExplain(variant)}
                    >
                      Why this number?
                    </Button>
                    {canEdit && product.active ? (
                      <RetireMenu
                        label={variant.sku}
                        active={variant.active}
                        scope="variant"
                        onSelect={() => onRetire({ product, variant })}
                      />
                    ) : null}
                  </Group>
                </Table.Td>
              </Table.Tr>
            );
          })
        : null}
    </>
  );
}
