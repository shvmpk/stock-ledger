import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Anchor, Badge, Box, Button, Group, Stack, Table, Text, TextInput } from '@mantine/core';
import { IconChartBar, IconFilterOff } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { listShippingSummary } from '../../lib/api/endpoints/analytics';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { TableSkeleton } from '../../components/feedback/TableSkeleton';
import { formatMonth } from '../../lib/format/date';
import { formatMinorUnits, toMinorUnits } from '../../lib/format/money';
import { formatQuantity } from '../../lib/format/quantity';

export function ShippingSummaryPage() {
  const [month, setMonth] = useState('');
  const query = useQuery({
    queryKey: queryKeys.shippingSummary(),
    queryFn: () => listShippingSummary(),
  });

  const rows = useMemo(() => {
    const data = query.data ?? [];
    return month ? data.filter((row) => row.period === month) : data;
  }, [query.data, month]);

  const totals = useMemo(
    () =>
      rows.reduce(
        (sum, row) => ({
          orders: sum.orders + row.totalOrdersShipped,
          charged: sum.charged + toMinorUnits(row.totalShippingCharged),
          cost: sum.cost + toMinorUnits(row.totalShippingCost),
        }),
        { orders: 0, charged: 0, cost: 0 },
      ),
    [rows],
  );
  const net = totals.charged - totals.cost;
  const maxBar = rows.reduce(
    (max, row) => Math.max(max, toMinorUnits(row.totalShippingCharged), toMinorUnits(row.totalShippingCost)),
    0,
  );

  return (
    <>
      <PageHeader
        title="Shipping summary"
        description="Monthly reconciliation of shipping charged to customers versus actual carrier postage costs."
        actions={
          <Button
            component={Link}
            to="/orders/shipped"
            variant="default"
            leftSection={<IconChartBar size={16} />}
          >
            Open shipped orders
          </Button>
        }
      />

      <Group justify="space-between" align="flex-end" mb="md" wrap="wrap">
        <TextInput
          type="month"
          label="Month"
          value={month}
          onChange={(event) => setMonth(event.currentTarget.value)}
          w={200}
        />
        <Button
          variant="subtle"
          leftSection={<IconFilterOff size={16} />}
          onClick={() => setMonth('')}
          disabled={!month}
        >
          Show all months
        </Button>
      </Group>

      <Box className="sl-summary-strip" mb="md" aria-label="Shipping summary totals">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(totals.orders)}</Text>
          <Text className="sl-summary-label">Orders shipped</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatMinorUnits(totals.charged)}</Text>
          <Text className="sl-summary-label">Shipping charged</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatMinorUnits(totals.cost)}</Text>
          <Text className="sl-summary-label">Actual cost</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text
            className="sl-summary-value"
            c={net < 0 ? 'var(--sl-color-danger)' : 'var(--sl-color-success)'}
          >
            {formatMinorUnits(net)}
          </Text>
          <Text className="sl-summary-label">Net shipping margin</Text>
        </Box>
      </Box>

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.isLoading ? (
        <TableSkeleton rows={6} columns={6} />
      ) : rows.length === 0 ? (
        <EmptyState
          title={month ? `No shipments in ${formatMonth(month)}` : 'No shipping history yet'}
          description="Shipping summaries appear after orders are dispatched with a carrier and actual cost."
          action={
            month ? (
              <Button variant="light" onClick={() => setMonth('')}>
                Show all months
              </Button>
            ) : (
              <Button component={Link} to="/orders/ready" variant="light">
                Open ready-to-ship orders
              </Button>
            )
          }
        />
      ) : (
        <Table className="sl-data-table" captionSide="top">
          <Table.Caption className="sl-table-caption">
            Monthly shipping economics, newest month first
          </Table.Caption>
          <Table.Thead>
            <Table.Tr>
              <Table.Th scope="col">Month</Table.Th>
              <Table.Th scope="col" ta="right">
                Orders
              </Table.Th>
              <Table.Th scope="col" ta="right">
                Charged
              </Table.Th>
              <Table.Th scope="col" ta="right">
                Actual cost
              </Table.Th>
              <Table.Th scope="col" ta="right">
                Net margin
              </Table.Th>
              <Table.Th scope="col">Charged vs cost</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((row) => {
              const charged = toMinorUnits(row.totalShippingCharged);
              const cost = toMinorUnits(row.totalShippingCost);
              const margin = charged - cost;
              return (
                <Table.Tr key={row.period}>
                  <Table.Th scope="row" fw={600}>
                    {formatMonth(row.period)}
                  </Table.Th>
                  <Table.Td ta="right" className="sl-tabular">
                    {formatQuantity(row.totalOrdersShipped)}
                  </Table.Td>
                  <Table.Td ta="right" className="sl-tabular">
                    {formatMinorUnits(charged)}
                  </Table.Td>
                  <Table.Td ta="right" className="sl-tabular">
                    {formatMinorUnits(cost)}
                  </Table.Td>
                  <Table.Td ta="right">
                    <Stack gap={2} align="flex-end">
                      <Text
                        size="sm"
                        fw={700}
                        className="sl-tabular"
                        c={margin < 0 ? 'var(--sl-color-danger)' : 'var(--sl-color-success)'}
                      >
                        {formatMinorUnits(margin)}
                      </Text>
                      {margin < 0 ? (
                        <Badge color="red" variant="light" size="xs">
                          Loss
                        </Badge>
                      ) : (
                        <Badge color="green" variant="light" size="xs">
                          Margin
                        </Badge>
                      )}
                    </Stack>
                  </Table.Td>
                  <Table.Td>
                    <Stack gap={4} miw={170}>
                      <Box className="sl-bar-track" aria-hidden="true">
                        <Box
                          className="sl-bar-cost"
                          style={{ width: `${maxBar ? (cost / maxBar) * 100 : 0}%` }}
                        />
                        <Box
                          className="sl-bar-charged"
                          style={{ width: `${maxBar ? (charged / maxBar) * 100 : 0}%` }}
                        />
                      </Box>
                      <Group gap="sm">
                        <Text size="10px" c="dimmed">
                          Charged
                        </Text>
                        <Text size="10px" c="dimmed">
                          Cost
                        </Text>
                      </Group>
                    </Stack>
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      )}

      <Text size="xs" c="dimmed" mt="md">
        Staff accounts cannot open this owner-only view. Use{' '}
        <Anchor component={Link} to="/orders/shipped">
          shipped orders
        </Anchor>{' '}
        for operational tracking.
      </Text>
    </>
  );
}
