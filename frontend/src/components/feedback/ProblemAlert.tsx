import { Alert, Button, Group, List, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconRefresh } from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../lib/api/errors';
import type { StockAction } from '../../lib/api/types';

function actionHref(action: StockAction, sku: string | undefined): string | null {
  const encodedSku = sku ? encodeURIComponent(sku) : '';
  switch (action.code) {
    case 'RECEIVE_STOCK':
      return `/stock/products?receiveSku=${encodedSku}`;
    case 'VIEW_MOVEMENTS':
      return `/history/movements?sku=${encodedSku}`;
    case 'VIEW_RESERVATIONS':
      return `/stock/products?explainSku=${encodedSku}`;
    case 'VIEW_RETIRED_PRODUCTS':
      return '/stock/products?retired=1';
    default:
      return null;
  }
}

export function ProblemAlert({
  error,
  title,
  onRetry,
  compact = false,
}: {
  error: unknown;
  title?: string;
  onRetry?: () => void;
  compact?: boolean;
}) {
  if (!error) return null;
  const apiError = isApiError(error) ? error : null;
  const detail = apiError?.detail ?? (error instanceof Error ? error.message : 'Something went wrong.');
  const heading = title ?? apiError?.title ?? 'Something went wrong';
  const invalidParams = apiError?.invalidParams.filter((param) => param.message) ?? [];
  const hasStockFigures =
    apiError?.onHandQuantity !== undefined &&
    apiError?.reservedQuantity !== undefined &&
    apiError?.availableQuantity !== undefined;
  const reservations = apiError?.reservedBy ?? [];
  const actions = apiError?.actions ?? [];

  return (
    <Alert
      color="red"
      variant="light"
      title={heading}
      icon={<IconAlertTriangle size={18} />}
      role="alert"
      my={compact ? 0 : 'md'}
    >
      <Stack gap="xs">
        <Text size="sm">{detail}</Text>
        {apiError?.sku ? (
          <Text size="xs" c="dimmed" className="sl-code">
            {apiError.sku}
          </Text>
        ) : null}
        {hasStockFigures ? (
          <Text size="sm" className="sl-tabular" fw={600}>
            {apiError.onHandQuantity} on hand · {apiError.reservedQuantity} reserved ·{' '}
            {apiError.availableQuantity} available
          </Text>
        ) : null}
        {reservations.length > 0 ? (
          <Text size="xs" c="dimmed">
            Held by{' '}
            {reservations
              .map((holder) => `${holder.orderNumber} (${holder.reservedQuantity})`)
              .join(', ')}
          </Text>
        ) : null}
        {apiError?.code ? (
          <Text size="xs" c="dimmed" className="sl-code">
            {apiError.code}
          </Text>
        ) : null}
        {invalidParams.length > 0 ? (
          <List size="xs" spacing={2}>
            {invalidParams.map((param) => (
              <List.Item key={`${param.field}-${param.message}`}>
                <Text span fw={600} size="xs">
                  {param.field}
                </Text>
                : {param.message}
              </List.Item>
            ))}
          </List>
        ) : null}
        {actions.length > 0 ? (
          <Group gap="xs" wrap="wrap">
            {actions.map((action) => {
              const href = actionHref(action, apiError?.sku);
              return href ? (
                <Button
                  key={action.code}
                  component={Link}
                  to={href}
                  size="xs"
                  variant="light"
                  color="red"
                >
                  {action.label}
                </Button>
              ) : (
                <Text key={action.code} size="xs" fw={600}>
                  {action.label}
                </Text>
              );
            })}
          </Group>
        ) : null}
        {onRetry ? (
          <Group>
            <Button
              size="xs"
              variant="light"
              color="red"
              leftSection={<IconRefresh size={14} />}
              onClick={onRetry}
            >
              Try again
            </Button>
          </Group>
        ) : null}
      </Stack>
    </Alert>
  );
}
