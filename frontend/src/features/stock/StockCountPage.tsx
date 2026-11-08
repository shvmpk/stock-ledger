import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Drawer,
  Group,
  Loader,
  NumberInput,
  Progress,
  Select,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconArrowRight,
  IconCheck,
  IconClipboardList,
  IconHistory,
  IconLayoutGrid,
  IconPlayerStop,
  IconRefresh,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listProducts } from '../../lib/api/endpoints/products';
import {
  addStockCountItem,
  cancelStockCount,
  completeStockCount,
  createStockCount,
  getStockCount,
  listStockCounts,
} from '../../lib/api/endpoints/inventory';
import type { Product, ProductVariant, StockCount, StockCountSummary } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog';
import { SearchInput } from '../../components/data/SearchInput';
import { FilterBar } from '../../components/data/FilterBar';
import { CategoryIcon } from '../../components/domain/CategoryIcon';
import { VariantAvatar } from '../../components/domain/VariantAvatar';
import { formatDateTime } from '../../lib/format/date';
import { formatQuantity, varianceLabel } from '../../lib/format/quantity';
import { formatDelta } from '../../lib/format/quantity';

interface VariantEntry {
  product: Product;
  variant: ProductVariant;
}

function filterEntries(products: Product[], search: string, category: string): VariantEntry[] {
  const query = search.trim().toLowerCase();
  return products
    .flatMap((product) => product.variants.map((variant) => ({ product, variant })))
    .filter((entry) => category === 'all' || entry.product.category === category)
    .filter((entry) => {
      if (!query) return true;
      return [
        entry.product.name,
        entry.product.category,
        entry.variant.sku,
        entry.variant.barcode,
        entry.variant.optionValue,
      ]
        .join(' ')
        .toLowerCase()
        .includes(query);
    })
    .sort(
      (a, b) =>
        a.product.name.localeCompare(b.product.name) ||
        a.variant.optionValue.localeCompare(b.variant.optionValue),
    );
}

function statusColor(status: StockCountSummary['status']): string {
  if (status === 'COMPLETED') return 'green';
  if (status === 'CANCELLED') return 'gray';
  return 'blue';
}

function SessionSummary({ session }: { session: StockCount }) {
  return (
    <Group gap="lg" wrap="wrap">
      <Text size="sm" component="div">
        Status:{' '}
        <Badge color={statusColor(session.status)} variant="light" size="sm">
          {session.status.replace('_', ' ')}
        </Badge>
      </Text>
      <Text size="sm">
        Started <Text span fw={600}>{formatDateTime(session.startedAt)}</Text>
      </Text>
      {session.conductedBy ? (
        <Text size="sm">
          Operator <Text span fw={600}>{session.conductedBy.fullName}</Text>
        </Text>
      ) : null}
      <Text size="sm">
        Items <Text span fw={600}>{formatQuantity(session.itemCount)}</Text>
      </Text>
      <Text size="sm">
        Variance{' '}
        <Text span fw={600} className="sl-tabular">
          {formatDelta(session.totalVariance)}
        </Text>
      </Text>
    </Group>
  );
}

export function StockCountPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const requestedSession = searchParams.get('session');
  const productsQuery = useQuery({ queryKey: queryKeys.products, queryFn: () => listProducts() });
  const recentQuery = useQuery({
    queryKey: queryKeys.stockCounts({ page: 0, size: 10 }),
    queryFn: () => listStockCounts({ page: 0, size: 10 }),
  });

  const [session, setSession] = useState<StockCount | null>(null);
  const [counts, setCounts] = useState<Record<string, number | ''>>({});
  const [recordedVariantIds, setRecordedVariantIds] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState(searchParams.get('sku') ?? '');
  const [category, setCategory] = useState('all');
  const [reviewOpen, setReviewOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [completed, setCompleted] = useState<StockCount | null>(null);
  const bootstrapped = useRef(false);
  const lastRequestedSession = useRef<string | null>(null);
  const lastResumeId = useRef<string | null>(null);

  const products = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);
  const categories = useMemo(
    () => Array.from(new Set(products.map((product) => product.category))).sort(),
    [products],
  );

  const entries = useMemo(
    () => filterEntries(products, search, category),
    [products, search, category],
  );

  const applySession = (loaded: StockCount) => {
    const next: Record<string, number | ''> = {};
    for (const item of loaded.items) {
      next[item.productVariantId] = item.countedOnHand;
    }
    setCounts(next);
    setRecordedVariantIds(new Set(loaded.items.map((item) => item.productVariantId)));
  };

  const startMutation = useMutation({
    mutationFn: createStockCount,
    onSuccess: async (created) => {
      setSession(created);
      setCompleted(null);
      setCounts({});
      setRecordedVariantIds(new Set());
      setError(null);
      lastResumeId.current = null;
      await queryClient.invalidateQueries({ queryKey: ['stockCounts'] });
    },
  });

  const resumeMutation = useMutation({
    mutationFn: getStockCount,
    onSuccess: (loaded) => {
      setSession(loaded);
      applySession(loaded);
      setCompleted(loaded.status === 'COMPLETED' ? loaded : null);
      setError(null);
      lastResumeId.current = loaded.id;
    },
  });

  const starting = startMutation.isPending || resumeMutation.isPending;
  const loadError = startMutation.error ?? resumeMutation.error ?? error;

  const startSession = () => {
    setError(null);
    setCompleted(null);
    lastResumeId.current = null;
    startMutation.reset();
    resumeMutation.reset();
    startMutation.mutate();
  };

  const resumeSession = (id: string) => {
    setError(null);
    setCompleted(null);
    lastResumeId.current = id;
    startMutation.reset();
    resumeMutation.reset();
    resumeMutation.mutate(id);
  };

  useEffect(() => {
    if (productsQuery.isLoading || recentQuery.isLoading || productsQuery.isError) return;

    if (requestedSession && requestedSession !== lastRequestedSession.current) {
      lastRequestedSession.current = requestedSession;
      bootstrapped.current = true;
      lastResumeId.current = requestedSession;
      resumeMutation.mutate(requestedSession);
      return;
    }

    if (bootstrapped.current) return;
    bootstrapped.current = true;
    const active = recentQuery.data?.data.find((item) => item.status === 'IN_PROGRESS');
    if (active) {
      lastResumeId.current = active.id;
      resumeMutation.mutate(active.id);
    } else {
      startMutation.mutate();
    }
  }, [
    productsQuery.isLoading,
    productsQuery.isError,
    recentQuery.isLoading,
    recentQuery.data,
    requestedSession,
    resumeMutation,
    startMutation,
  ]);

  const changed = useMemo(() => {
    return entries
      .map((entry) => {
        const counted = counts[entry.variant.id];
        return {
          ...entry,
          counted: typeof counted === 'number' ? counted : null,
        };
      })
      .filter(
        (entry): entry is VariantEntry & { counted: number } =>
          entry.counted !== null &&
          entry.counted !== entry.variant.onHand &&
          !recordedVariantIds.has(entry.variant.id),
      );
  }, [entries, counts, recordedVariantIds]);

  const changedVariance = changed.reduce(
    (sum, entry) => sum + (entry.counted - entry.variant.onHand),
    0,
  );
  const recordedCount = recordedVariantIds.size;
  const inProgress = session?.status === 'IN_PROGRESS';

  const setCount = (variantId: string, value: string | number) => {
    setCounts((current) => ({
      ...current,
      [variantId]: typeof value === 'number' ? value : '',
    }));
  };

  const submitAndComplete = async () => {
    if (!session) return;
    if (changed.length === 0 && recordedCount === 0) {
      setSubmitError(new Error('Record at least one physical count before completing the session.'));
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const entry of changed) {
        await addStockCountItem(session.id, {
          productVariantId: entry.variant.id,
          countedOnHand: entry.counted,
          notes: `Shelf count: expected ${entry.variant.onHand}, counted ${entry.counted}`,
        });
        setRecordedVariantIds((current) => {
          const next = new Set(current);
          next.add(entry.variant.id);
          return next;
        });
      }
      const finished = await completeStockCount(session.id);
      setSession(finished);
      setCompleted(finished);
      setReviewOpen(false);
      notifications.show({
        title: 'Stock count completed',
        message:
          finished.hasReservationDeficit
            ? `Count recorded. Physical stock is below reservations by ${finished.reservationDeficit} units.`
            : 'Count recorded and audit movements written.',
        color: finished.hasReservationDeficit ? 'amber' : 'emerald',
        icon: <IconCheck size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.products }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
        queryClient.invalidateQueries({ queryKey: ['stockCounts'] }),
      ]);
    } catch (caught) {
      setSubmitError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  const cancelSession = async () => {
    if (!session) return;
    setSubmitting(true);
    setError(null);
    try {
      const cancelled = await cancelStockCount(session.id);
      setSession(cancelled);
      setCancelOpen(false);
      setCounts({});
      setRecordedVariantIds(new Set());
      notifications.show({
        title: 'Stock count cancelled',
        message: 'Adjustments already recorded remain in the ledger.',
        color: 'gray',
      });
      await queryClient.invalidateQueries({ queryKey: ['stockCounts'] });
    } catch (caught) {
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  if (productsQuery.isError) {
    return (
      <>
        <PageHeader title="Stock count" description="Audit physical shelf stock against the ledger." />
        <ErrorState error={productsQuery.error} onRetry={() => void productsQuery.refetch()} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Stock count"
        description="Count physical stock, review every variance, and write an auditable correction."
        actions={
          <>
            {inProgress ? (
              <>
                <Button
                  variant="default"
                  color="red"
                  leftSection={<IconPlayerStop size={16} />}
                  onClick={() => setCancelOpen(true)}
                  disabled={submitting}
                >
                  Cancel session
                </Button>
                <Button
                  leftSection={<IconClipboardList size={16} />}
                  onClick={() => setReviewOpen(true)}
                  disabled={starting}
                >
                  Review counts
                </Button>
              </>
            ) : (
              <Button
                leftSection={<IconRefresh size={16} />}
                onClick={() => void startSession()}
                loading={starting}
              >
                Start new count
              </Button>
            )}
          </>
        }
      />

      {starting && !session ? (
        <Card withBorder padding="lg">
          <Group>
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              Starting a stock-count session…
            </Text>
          </Group>
        </Card>
      ) : loadError ? (
        <Stack gap="md">
          <ProblemAlert
            error={loadError}
            title="Stock count unavailable"
            onRetry={() => {
              const id = lastResumeId.current;
              if (id) resumeSession(id);
              else startSession();
            }}
          />
        </Stack>
      ) : completed ? (
        <CompletedPanel
          session={completed}
          onStartAnother={() => void startSession()}
          onViewHistory={() => navigate('/history/movements?reason=AUDIT_CORRECTION')}
        />
      ) : session && session.status === 'CANCELLED' ? (
        <Card withBorder padding="lg">
          <Stack gap="sm">
            <Title order={2} size="h4">
              Session cancelled
            </Title>
            <Text size="sm" c="dimmed">
              Adjustments already recorded remain in the stock ledger. Start another session when the
              shelves are ready.
            </Text>
            <Group>
              <Button leftSection={<IconRefresh size={16} />} onClick={() => void startSession()}>
                Start new count
              </Button>
              <Button variant="default" onClick={() => navigate('/history/movements?reason=AUDIT_CORRECTION')}>
                View audit movements
              </Button>
            </Group>
          </Stack>
        </Card>
      ) : session ? (
        <>
          <Card withBorder padding="md" mb="md">
            <Stack gap="xs">
              <Group justify="space-between" align="flex-start" wrap="wrap">
                <SessionSummary session={session} />
                <Badge color="blue" variant="light">
                  In progress
                </Badge>
              </Group>
              <Progress
                value={recordedCount > 0 ? 35 : 5}
                color="emerald"
                size="xs"
                aria-label="Stock count progress"
              />
              <Text size="xs" c="dimmed">
                {recordedCount === 0
                  ? 'No counts recorded yet. Enter a physical count for at least one variant.'
                  : `${formatQuantity(recordedCount)} counted variants recorded. Review and complete when ready.`}
              </Text>
            </Stack>
          </Card>

          <FilterBar>
            <Box style={{ flex: '1 1 260px', maxWidth: 420 }}>
              <SearchInput
                value={search}
                onChange={setSearch}
                placeholder="Search product, SKU, barcode, or option"
                label="Search variants to count"
              />
            </Box>
            <Select
              label="Category"
              value={category}
              onChange={(value) => setCategory(value ?? 'all')}
              data={[
                { value: 'all', label: 'All categories' },
                ...categories.map((item) => ({ value: item, label: item })),
              ]}
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
          </FilterBar>

          {entries.length === 0 ? (
            <EmptyState
              title="No variants match"
              description="Clear the search or choose another category to see shelf items."
            />
          ) : (
            <>
              <Box visibleFrom="md">
                <Table className="sl-data-table" captionSide="top">
                  <Table.Caption className="sl-table-caption">
                    Expected on-hand comes from the ledger. Enter the physical count you observe on the shelf.
                  </Table.Caption>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th scope="col">Product / variant</Table.Th>
                      <Table.Th scope="col" ta="right">
                        Expected on hand
                      </Table.Th>
                      <Table.Th scope="col" ta="right">
                        Reserved
                      </Table.Th>
                      <Table.Th scope="col">Counted on hand</Table.Th>
                      <Table.Th scope="col" ta="right">
                        Variance
                      </Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {entries.map(({ product, variant }) => {
                      const counted = counts[variant.id];
                      const numeric = typeof counted === 'number' ? counted : null;
                      const variance = numeric === null ? null : numeric - variant.onHand;
                      return (
                        <Table.Tr key={variant.id}>
                          <Table.Th scope="row" fw={500}>
                            <Group gap="sm" wrap="nowrap" align="flex-start">
                              <VariantAvatar
                                sku={variant.sku}
                                optionValue={variant.optionValue}
                                size="sm"
                              />
                              <Box>
                                <Text size="sm" fw={600}>
                                  {product.name}
                                </Text>
                                <Text size="xs" c="dimmed">
                                  {variant.optionValue} · <span className="sl-code">{variant.sku}</span>
                                </Text>
                              </Box>
                            </Group>
                          </Table.Th>
                          <Table.Td ta="right" className="sl-tabular">
                            {formatQuantity(variant.onHand)}
                          </Table.Td>
                          <Table.Td ta="right" className="sl-tabular">
                            {formatQuantity(variant.reserved)}
                          </Table.Td>
                          <Table.Td>
                            <NumberInput
                              aria-label={`Counted on hand for ${variant.sku}`}
                              value={counted ?? ''}
                              onChange={(value) => setCount(variant.id, value)}
                              min={0}
                              allowDecimal={false}
                              placeholder={String(variant.onHand)}
                              w={120}
                              size="xs"
                            />
                          </Table.Td>
                          <Table.Td ta="right">
                            <Variance value={variance} />
                          </Table.Td>
                        </Table.Tr>
                      );
                    })}
                  </Table.Tbody>
                </Table>
              </Box>

              <Stack hiddenFrom="md" gap="sm">
                {entries.map(({ product, variant }) => {
                  const counted = counts[variant.id];
                  const numeric = typeof counted === 'number' ? counted : null;
                  const variance = numeric === null ? null : numeric - variant.onHand;
                  return (
                    <Card key={variant.id} withBorder padding="sm" radius="md">
                      <Stack gap="xs">
                        <Group gap="sm" wrap="nowrap" align="flex-start">
                          <VariantAvatar
                            sku={variant.sku}
                            optionValue={variant.optionValue}
                            size="sm"
                          />
                          <Box>
                            <Text size="sm" fw={600}>
                              {product.name}
                            </Text>
                            <Text size="xs" c="dimmed">
                              {variant.optionValue} · <span className="sl-code">{variant.sku}</span>
                            </Text>
                          </Box>
                        </Group>
                        <Group justify="space-between">
                          <Text size="xs" c="dimmed">
                            Expected on hand
                          </Text>
                          <Text size="sm" className="sl-tabular">
                            {formatQuantity(variant.onHand)} · reserved {formatQuantity(variant.reserved)}
                          </Text>
                        </Group>
                        <NumberInput
                          label="Counted on hand"
                          aria-label={`Counted on hand for ${variant.sku}`}
                          value={counted ?? ''}
                          onChange={(value) => setCount(variant.id, value)}
                          min={0}
                          allowDecimal={false}
                          placeholder={String(variant.onHand)}
                          size="xs"
                        />
                        <Group justify="space-between">
                          <Text size="xs" c="dimmed">
                            Variance
                          </Text>
                          <Variance value={variance} />
                        </Group>
                      </Stack>
                    </Card>
                  );
                })}
              </Stack>
            </>
          )}
        </>
      ) : (
        <EmptyState
          title="No stock-count session"
          description="Start a session to compare physical shelf counts with the ledger."
          action={
            <Button leftSection={<IconRefresh size={16} />} onClick={() => void startSession()}>
              Start stock count
            </Button>
          }
        />
      )}

      <RecentSessions
        sessions={recentQuery.data?.data ?? []}
        loading={recentQuery.isLoading}
        onOpen={(id) => void resumeSession(id)}
      />

      <Drawer
        opened={reviewOpen}
        onClose={() => {
          if (!submitting) setReviewOpen(false);
        }}
        title="Review stock count"
        position="right"
        size="lg"
        closeOnClickOutside={!submitting}
        closeOnEscape={!submitting}
      >
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            Only changed variants are recorded. Completing the session replaces physical on-hand stock and
            writes immutable <span className="sl-code">AUDIT_CORRECTION</span> movements.
          </Text>

          {changed.length === 0 ? (
            <Alert color={recordedCount > 0 ? 'emerald' : 'blue'} variant="light">
              {recordedCount > 0
                ? `${formatQuantity(recordedCount)} counts are already recorded. Complete the session to apply the audit.`
                : 'No changes recorded yet. Enter a physical count that differs from expected on-hand.'}
            </Alert>
          ) : (
            <Stack gap="xs">
              <Group justify="space-between">
                <Text fw={600} size="sm">
                  Changed variants
                </Text>
                <Text size="sm" className="sl-tabular">
                  Total variance {formatDelta(changedVariance)}
                </Text>
              </Group>
              <Table className="sl-data-table">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th scope="col">Variant</Table.Th>
                    <Table.Th scope="col" ta="right">
                      Expected
                    </Table.Th>
                    <Table.Th scope="col" ta="right">
                      Counted
                    </Table.Th>
                    <Table.Th scope="col" ta="right">
                      Variance
                    </Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {changed.map((entry) => (
                    <Table.Tr key={entry.variant.id}>
                      <Table.Th scope="row" fw={500}>
                        <Text size="sm">{entry.product.name}</Text>
                        <Text size="xs" c="dimmed" className="sl-code">
                          {entry.variant.sku}
                        </Text>
                      </Table.Th>
                      <Table.Td ta="right" className="sl-tabular">
                        {formatQuantity(entry.variant.onHand)}
                      </Table.Td>
                      <Table.Td ta="right" className="sl-tabular">
                        {formatQuantity(entry.counted)}
                      </Table.Td>
                      <Table.Td ta="right">
                        <Variance value={entry.counted - entry.variant.onHand} />
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Stack>
          )}

          <Alert color="amber" variant="light" icon={<IconAlertTriangle size={16} />}>
            Physical stock is replaced by your count. If counted stock is below reserved stock, a deficit
            banner appears after completion.
          </Alert>

          {submitError ? <ProblemAlert error={submitError} title="Could not complete count" /> : null}

          <Group justify="flex-end">
            <Button variant="default" onClick={() => setReviewOpen(false)} disabled={submitting}>
              Keep counting
            </Button>
            <Button
              onClick={() => void submitAndComplete()}
              loading={submitting}
              disabled={changed.length === 0 && recordedCount === 0}
              leftSection={<IconCheck size={16} />}
            >
              Record &amp; complete
            </Button>
          </Group>
        </Stack>
      </Drawer>

      <ConfirmDialog
        opened={cancelOpen}
        title="Cancel stock-count session?"
        confirmLabel="Cancel session"
        danger
        loading={submitting}
        onClose={() => setCancelOpen(false)}
        onConfirm={() => void cancelSession()}
      >
        Adjustments already recorded stay in the ledger. Unrecorded counts are discarded.
      </ConfirmDialog>
    </>
  );
}

function Variance({ value }: { value: number | null }) {
  if (value === null) {
    return (
      <Text size="xs" c="dimmed">
        Not counted
      </Text>
    );
  }
  const color = value === 0 ? 'dimmed' : value > 0 ? 'var(--sl-color-warning)' : 'var(--sl-color-danger)';
  return (
    <Text size="sm" fw={600} className="sl-tabular" c={color}>
      {varianceLabel(value)}
    </Text>
  );
}

function CompletedPanel({
  session,
  onStartAnother,
  onViewHistory,
}: {
  session: StockCount;
  onStartAnother: () => void;
  onViewHistory: () => void;
}) {
  return (
    <Stack gap="md">
      <Alert color="emerald" variant="light" icon={<IconCheck size={18} />} title="Stock count completed">
        <Stack gap="xs">
          <SessionSummary session={session} />
          <Text size="sm">
            {formatQuantity(session.itemCount)} variants recorded. Every adjustment wrote an immutable audit
            movement.
          </Text>
        </Stack>
      </Alert>

      {session.hasReservationDeficit ? (
        <Alert
          color="red"
          variant="light"
          icon={<IconAlertTriangle size={18} />}
          title="Physical count is below reserved stock"
        >
          <Stack gap="xs">
            <Text size="sm">
              Physical count is below reserved stock by {formatQuantity(session.reservationDeficit)} units.
              Review reserved orders and reorder guidance before taking new orders.
            </Text>
            <Group>
              <Button component={Link} to="/orders/new" size="xs" variant="light" color="red">
                Review reserved orders
              </Button>
              <Button component={Link} to="/stock/reorder" size="xs" variant="light" color="red">
                Review reorder list
              </Button>
            </Group>
          </Stack>
        </Alert>
      ) : null}

      {session.items.length > 0 ? (
        <Table className="sl-data-table" captionSide="top">
          <Table.Caption className="sl-table-caption">Recorded variances</Table.Caption>
          <Table.Thead>
            <Table.Tr>
              <Table.Th scope="col">Variant</Table.Th>
              <Table.Th scope="col" ta="right">
                Expected
              </Table.Th>
              <Table.Th scope="col" ta="right">
                Counted
              </Table.Th>
              <Table.Th scope="col" ta="right">
                Variance
              </Table.Th>
              <Table.Th scope="col">Deficit</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {session.items.map((item) => (
              <Table.Tr key={item.id}>
                <Table.Th scope="row" fw={500}>
                  <Text size="sm">{item.productName}</Text>
                  <Text size="xs" c="dimmed" className="sl-code">
                    {item.sku}
                  </Text>
                </Table.Th>
                <Table.Td ta="right" className="sl-tabular">
                  {formatQuantity(item.expectedOnHand)}
                </Table.Td>
                <Table.Td ta="right" className="sl-tabular">
                  {formatQuantity(item.countedOnHand)}
                </Table.Td>
                <Table.Td ta="right">
                  <Variance value={item.variance} />
                </Table.Td>
                <Table.Td>
                  {item.hasReservationDeficit ? (
                    <Badge color="red" variant="light">
                      {formatQuantity(item.reservationDeficit)} short
                    </Badge>
                  ) : (
                    <Text size="xs" c="dimmed">
                      —
                    </Text>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      ) : null}

      <Group>
        <Button leftSection={<IconRefresh size={16} />} onClick={onStartAnother}>
          Start another count
        </Button>
        <Button variant="default" leftSection={<IconHistory size={16} />} onClick={onViewHistory}>
          View audit movements
        </Button>
      </Group>
    </Stack>
  );
}

function RecentSessions({
  sessions,
  loading,
  onOpen,
}: {
  sessions: StockCountSummary[];
  loading: boolean;
  onOpen: (id: string) => void;
}) {
  if (loading) {
    return (
      <Group mt="xl">
        <Loader size="sm" />
        <Text size="sm" c="dimmed">
          Loading recent sessions…
        </Text>
      </Group>
    );
  }
  if (sessions.length === 0) return null;

  return (
    <Box mt="xl">
      <Group justify="space-between" mb="sm">
        <Title order={2} size="h4">
          Recent sessions
        </Title>
        <Text size="xs" c="dimmed">
          Newest first
        </Text>
      </Group>
      <Table className="sl-data-table" captionSide="top">
        <Table.Caption className="sl-table-caption">Stock-count session history</Table.Caption>
        <Table.Thead>
          <Table.Tr>
            <Table.Th scope="col">Started</Table.Th>
            <Table.Th scope="col">Operator</Table.Th>
            <Table.Th scope="col">Status</Table.Th>
            <Table.Th scope="col" ta="right">
              Items
            </Table.Th>
            <Table.Th scope="col" ta="right">
              Variance
            </Table.Th>
            <Table.Th scope="col" ta="right">
              Action
            </Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {sessions.map((session) => (
            <Table.Tr key={session.id}>
              <Table.Th scope="row" fw={500}>
                <Text size="sm">{formatDateTime(session.startedAt)}</Text>
                {session.completedAt ? (
                  <Text size="xs" c="dimmed">
                    Completed {formatDateTime(session.completedAt)}
                  </Text>
                ) : null}
              </Table.Th>
              <Table.Td>{session.conductedBy?.fullName ?? '—'}</Table.Td>
              <Table.Td>
                <Badge color={statusColor(session.status)} variant="light" size="sm">
                  {session.status.replace('_', ' ')}
                </Badge>
              </Table.Td>
              <Table.Td ta="right" className="sl-tabular">
                {formatQuantity(session.itemCount)}
              </Table.Td>
              <Table.Td ta="right" className="sl-tabular">
                {formatDelta(session.totalVariance)}
              </Table.Td>
              <Table.Td ta="right">
                <Button
                  size="compact-xs"
                  variant="subtle"
                  rightSection={<IconArrowRight size={13} />}
                  onClick={() => onOpen(session.id)}
                >
                  {session.status === 'IN_PROGRESS' ? 'Resume' : 'View'}
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Box>
  );
}
