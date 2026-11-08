import {
  Anchor,
  Badge,
  Box,
  Button,
  Divider,
  Drawer,
  Group,
  List,
  Loader,
  Paper,
  Stack,
  Text,
} from '@mantine/core';
import { IconArrowRight, IconHelpCircle } from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getStockExplanation } from '../../lib/api/endpoints/inventory';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { OrderStatusBadge } from '../../components/data/StatusBadge';
import { Delta } from '../../components/data/Delta';
import { formatDateTime } from '../../lib/format/date';
import { formatQuantity } from '../../lib/format/quantity';
import type { StockExplanation } from '../../lib/api/types';

function Figure({ value, label }: { value: number; label: string }) {
  return (
    <Box className="sl-summary-item" style={{ minWidth: 92 }}>
      <Text className="sl-summary-value sl-tabular">{formatQuantity(value)}</Text>
      <Text className="sl-summary-label">{label}</Text>
    </Box>
  );
}

function ExplanationBody({ explanation }: { explanation: StockExplanation }) {
  const reorderLevel =
    explanation.reorderLevel === null
      ? 'No reorder level is set for this variant.'
      : `Reorder level: ${formatQuantity(explanation.reorderLevel)} units.`;

  return (
    <Stack gap="lg">
      <Box>
        <Group gap="sm" align="flex-start" wrap="nowrap">
          <IconHelpCircle size={20} aria-hidden="true" />
          <Box>
            <Text fw={700}>{explanation.productName}</Text>
            <Text size="sm" c="dimmed">
              {explanation.optionName}: {explanation.optionValue} ·{' '}
              <Text span className="sl-code">
                {explanation.sku}
              </Text>
            </Text>
          </Box>
        </Group>
      </Box>

      {!explanation.sellable ? (
        <Badge color="gray" variant="light">
          Retired — this variant cannot be ordered or received
        </Badge>
      ) : null}

      <Box aria-label="Stock explanation figures" className="sl-summary-strip">
        <Figure value={explanation.onHandQuantity} label="on hand" />
        <Figure value={explanation.reservedQuantity} label="reserved" />
        <Figure value={explanation.availableQuantity} label="available" />
      </Box>

      <Stack gap="xs">
        <Text size="sm" fw={600}>
          What this means
        </Text>
        <List size="sm" spacing={4}>
          {explanation.sentences.map((sentence) => (
            <List.Item key={sentence}>{sentence}</List.Item>
          ))}
        </List>
        <Text size="xs" c="dimmed">
          {reorderLevel}
        </Text>
      </Stack>

      <Divider />

      <Stack gap="xs">
        <Group justify="space-between">
          <Text size="sm" fw={600}>
            Open reservations
          </Text>
          <Text size="xs" c="dimmed">
            {formatQuantity(explanation.reservations.length)} holding stock
          </Text>
        </Group>
        {explanation.reservations.length === 0 ? (
          <Text size="sm" c="dimmed">
            No open orders are holding this variant.
          </Text>
        ) : (
          <Stack gap="xs">
            {explanation.reservations.map((reservation) => (
              <Paper key={reservation.orderId} withBorder p="sm" radius="md">
                <Group justify="space-between" align="flex-start" wrap="wrap">
                  <Group gap="xs">
                    <Anchor
                      component={Link}
                      to={`/orders/new?order=${reservation.orderId}`}
                      size="sm"
                      className="sl-code"
                      fw={600}
                    >
                      {reservation.orderNumber}
                    </Anchor>
                    <OrderStatusBadge status={reservation.status} />
                  </Group>
                  <Text size="sm" className="sl-tabular">
                    {formatQuantity(reservation.reservedQuantity)} reserved
                  </Text>
                </Group>
                <Text size="xs" c="dimmed">
                  Ordered {formatDateTime(reservation.orderedAt)}
                </Text>
              </Paper>
            ))}
          </Stack>
        )}
      </Stack>

      <Divider />

      <Stack gap="xs">
        <Group justify="space-between">
          <Text size="sm" fw={600}>
            Movements in the last {formatQuantity(explanation.movementWindowDays)} days
          </Text>
          <Text size="xs" c="dimmed">
            Since {formatDateTime(explanation.windowStart)}
          </Text>
        </Group>
        {explanation.recentMovements.length === 0 ? (
          <Text size="sm" c="dimmed">
            No stock movements were recorded in this window.
          </Text>
        ) : (
          <Stack gap="xs">
            {explanation.recentMovements.map((movement) => (
              <Paper key={movement.reason} withBorder p="sm" radius="md">
                <Group justify="space-between" align="flex-start" wrap="wrap">
                  <Box>
                    <Text size="sm" fw={600}>
                      {movement.label}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {formatQuantity(movement.movementCount)}{' '}
                      {movement.movementCount === 1 ? 'movement' : 'movements'} · latest{' '}
                      {formatDateTime(movement.latestAt)}
                    </Text>
                  </Box>
                  <Group gap="md">
                    <Stack gap={0} align="flex-end">
                      <Text size="xs" c="dimmed">
                        On hand
                      </Text>
                      <Delta value={movement.netOnHand} label="Net on-hand change" />
                    </Stack>
                    <Stack gap={0} align="flex-end">
                      <Text size="xs" c="dimmed">
                        Reserved
                      </Text>
                      <Delta value={movement.netReserved} label="Net reserved change" />
                    </Stack>
                  </Group>
                </Group>
              </Paper>
            ))}
          </Stack>
        )}
        <Button
          component={Link}
          to={`/history/movements?sku=${encodeURIComponent(explanation.sku)}`}
          variant="light"
          size="xs"
          leftSection={<IconArrowRight size={14} />}
          w="fit-content"
        >
          View all movements
        </Button>
      </Stack>

      <Text size="xs" c="dimmed">
        Explained from the ledger at {formatDateTime(explanation.generatedAt)}.
      </Text>
    </Stack>
  );
}

export function StockExplanationDrawer({
  variantId,
  onClose,
}: {
  variantId: string | null;
  onClose: () => void;
}) {
  const explanationQuery = useQuery({
    queryKey: queryKeys.stockExplanation(variantId ?? ''),
    queryFn: () => getStockExplanation(variantId as string),
    enabled: !!variantId,
  });

  return (
    <Drawer
      opened={!!variantId}
      onClose={onClose}
      title="Why this number?"
      size="lg"
      position="right"
      overlayProps={{ backgroundOpacity: 0.35, blur: 2 }}
    >
      {explanationQuery.isLoading ? (
        <Group>
          <Loader size="sm" />
          <Text size="sm" c="dimmed">
            Reading the ledger…
          </Text>
        </Group>
      ) : explanationQuery.isError ? (
        <ProblemAlert error={explanationQuery.error} title="Explanation unavailable" />
      ) : explanationQuery.data ? (
        <ExplanationBody explanation={explanationQuery.data} />
      ) : null}
    </Drawer>
  );
}
