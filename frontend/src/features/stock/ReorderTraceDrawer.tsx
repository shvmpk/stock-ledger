import {
  Alert,
  Badge,
  Box,
  Drawer,
  Group,
  Loader,
  Stack,
  Table,
  Text,
} from '@mantine/core';
import { IconInfoCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getReorderDetail } from '../../lib/api/endpoints/reorder';
import type { ItemCondition, ReorderItem } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ErrorState } from '../../components/feedback/ErrorState';
import { SectionHeader } from '../../components/feedback/PageHeader';
import { OrderStatusBadge } from '../../components/data/StatusBadge';
import { formatDate, formatDateTime } from '../../lib/format/date';
import { formatQuantity } from '../../lib/format/quantity';

function conditionBadge(condition: ItemCondition) {
  return (
    <Badge color={condition === 'SELLABLE' ? 'green' : 'orange'} variant="light" size="sm">
      {condition === 'SELLABLE' ? 'Sellable' : 'Damaged'}
    </Badge>
  );
}

export function ReorderTraceDrawer({
  item,
  onClose,
}: {
  item: ReorderItem | null;
  onClose: () => void;
}) {
  const detailQuery = useQuery({
    queryKey: queryKeys.reorderDetail(item?.variantId ?? ''),
    queryFn: () => getReorderDetail(item?.variantId as string),
    enabled: Boolean(item),
  });

  if (!item) return null;
  const detail = detailQuery.data;

  return (
    <Drawer
      opened
      onClose={onClose}
      position="right"
      size="lg"
      title={
        <Stack gap={2}>
          <Text fw={700}>{item.productName}</Text>
          <Text size="xs" c="dimmed" className="sl-code">
            {item.sku} · {item.optionName}: {item.optionValue}
          </Text>
        </Stack>
      }
      overlayProps={{ backgroundOpacity: 0.35, blur: 2 }}
    >
      {detailQuery.isLoading ? (
        <Group>
          <Loader size="sm" />
          <Text size="sm" c="dimmed">
            Tracing shipped demand…
          </Text>
        </Group>
      ) : detailQuery.isError ? (
        <ErrorState
          error={detailQuery.error}
          title="This sales trace could not load"
          onRetry={() => void detailQuery.refetch()}
        />
      ) : detail ? (
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            Shipped orders and processed returns for {detail.sku} between{' '}
            {formatDate(detail.windowStart)} and {formatDate(detail.windowEnd)}.
          </Text>

          {item.historyStatus === 'INSUFFICIENT' ? (
            <Alert
              color="gray"
              variant="light"
              title="Not enough history for an estimate"
              icon={<IconInfoCircle size={18} />}
            >
              This variant has not yet shipped for at least 21 days with 5 net units sold. The
              ledger below shows what has genuinely shipped, but no cover or quantity is invented.
            </Alert>
          ) : null}

          <Box className="sl-summary-strip" aria-label="Demand trace summary">
            <Box className="sl-summary-item">
              <Text className="sl-summary-value">{formatQuantity(detail.unitsShipped)}</Text>
              <Text className="sl-summary-label">Units shipped</Text>
            </Box>
            <Box className="sl-summary-item">
              <Text className="sl-summary-value">{formatQuantity(detail.unitsReturned)}</Text>
              <Text className="sl-summary-label">Units returned</Text>
            </Box>
            <Box className="sl-summary-item">
              <Text className="sl-summary-value">{formatQuantity(detail.netUnitsSold)}</Text>
              <Text className="sl-summary-label">Net sold</Text>
            </Box>
          </Box>

          <Box>
            <SectionHeader
              title="Shipped orders"
              description={`${formatQuantity(detail.sales.length)} shipments counted as demand`}
            />
            {detail.sales.length === 0 ? (
              <Text size="sm" c="dimmed">
                No shipped orders in this window.
              </Text>
            ) : (
              <Table className="sl-data-table" captionSide="top">
                <Table.Caption className="sl-table-caption">
                  Orders counted as demand for {detail.sku}
                </Table.Caption>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th scope="col">Order</Table.Th>
                    <Table.Th scope="col">Shipped</Table.Th>
                    <Table.Th scope="col" style={{ textAlign: 'right' }}>
                      Units
                    </Table.Th>
                    <Table.Th scope="col">Status</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {detail.sales.map((line) => (
                    <Table.Tr key={`${line.orderId}-${line.shippedAt}`}>
                      <Table.Td>
                        <Text size="sm" className="sl-code">
                          {line.orderNumber}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{formatDateTime(line.shippedAt)}</Text>
                      </Table.Td>
                      <Table.Td style={{ textAlign: 'right' }}>
                        <Text size="sm" fw={600} className="sl-tabular">
                          {formatQuantity(line.quantity)}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <OrderStatusBadge status={line.status} />
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}
          </Box>

          <Box>
            <SectionHeader
              title="Returns"
              description={`${formatQuantity(detail.returns.length)} processed returns netted against demand`}
            />
            {detail.returns.length === 0 ? (
              <Text size="sm" c="dimmed">
                No processed returns in this window.
              </Text>
            ) : (
              <Table className="sl-data-table" captionSide="top">
                <Table.Caption className="sl-table-caption">
                  Returns netted against demand for {detail.sku}
                </Table.Caption>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th scope="col">Order</Table.Th>
                    <Table.Th scope="col">Processed</Table.Th>
                    <Table.Th scope="col" style={{ textAlign: 'right' }}>
                      Units
                    </Table.Th>
                    <Table.Th scope="col">Condition</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {detail.returns.map((line) => (
                    <Table.Tr key={`${line.orderId}-${line.processedAt}`}>
                      <Table.Td>
                        <Text size="sm" className="sl-code">
                          {line.orderNumber}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{formatDateTime(line.processedAt)}</Text>
                      </Table.Td>
                      <Table.Td style={{ textAlign: 'right' }}>
                        <Text size="sm" fw={600} className="sl-tabular">
                          {formatQuantity(line.quantity)}
                        </Text>
                      </Table.Td>
                      <Table.Td>{conditionBadge(line.condition)}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}
          </Box>

          <Text size="xs" c="dimmed">
            Only shipped orders count as demand. Pending, waiting, and cancelled orders are
            ignored. Returns are netted against the units sold; damaged returns still reduce demand
            even though they never return to sellable stock.
          </Text>
        </Stack>
      ) : null}
    </Drawer>
  );
}
