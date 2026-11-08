import type { ReactNode } from 'react';
import { Box, Card, Group, Stack, Table, Text } from '@mantine/core';
import { TableSkeleton } from '../feedback/TableSkeleton';
import { EmptyState } from '../feedback/EmptyState';

export interface DataTableColumn<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  align?: 'left' | 'right' | 'center';
  width?: number | string;
  rowHeader?: boolean;
}

export function DataTable<T>({
  data,
  columns,
  getRowKey,
  caption,
  loading = false,
  empty,
  onRowClick,
  rowAriaLabel,
  mobileCard,
  minWidth = 720,
}: {
  data: T[];
  columns: Array<DataTableColumn<T>>;
  getRowKey: (row: T) => string;
  caption: string;
  loading?: boolean;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
  rowAriaLabel?: (row: T) => string;
  mobileCard?: (row: T) => ReactNode;
  minWidth?: number;
}) {
  if (loading) {
    return <TableSkeleton columns={Math.min(columns.length, 7)} />;
  }

  if (data.length === 0) {
    return <>{empty ?? <EmptyState title="Nothing to show" description="No rows match this view yet." />}</>;
  }

  const table = (
    <Table.ScrollContainer minWidth={minWidth}>
      <Table className="sl-data-table" captionSide="top">
        <Table.Caption className="sl-table-caption">{caption}</Table.Caption>
        <Table.Thead>
          <Table.Tr>
            {columns.map((column) => (
              <Table.Th key={column.key} style={{ width: column.width, textAlign: column.align ?? 'left' }}>
                {column.header}
              </Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {data.map((row) => (
            <Table.Tr
              key={getRowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={
                onRowClick
                  ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
              tabIndex={onRowClick ? 0 : undefined}
              aria-label={rowAriaLabel?.(row)}
              style={onRowClick ? { cursor: 'pointer' } : undefined}
            >
              {columns.map((column) => {
                const content = column.render(row);
                return column.rowHeader ? (
                  <Table.Th
                    key={column.key}
                    scope="row"
                    style={{ textAlign: column.align ?? 'left', fontWeight: 500 }}
                  >
                    {content}
                  </Table.Th>
                ) : (
                  <Table.Td key={column.key} style={{ textAlign: column.align ?? 'left' }}>
                    {content}
                  </Table.Td>
                );
              })}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );

  if (!mobileCard) {
    return table;
  }

  return (
    <>
      <Box visibleFrom="md">{table}</Box>
      <Stack gap="sm" hiddenFrom="md" aria-label={caption}>
        {data.map((row) => (
          <Card
            key={getRowKey(row)}
            withBorder
            padding="sm"
            radius="md"
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            onKeyDown={
              onRowClick
                ? (event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      onRowClick(row);
                    }
                  }
                : undefined
            }
            tabIndex={onRowClick ? 0 : undefined}
            aria-label={rowAriaLabel?.(row)}
            style={onRowClick ? { cursor: 'pointer' } : undefined}
          >
            {mobileCard(row)}
          </Card>
        ))}
      </Stack>
    </>
  );
}

export function MobileRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Group justify="space-between" gap="sm" wrap="nowrap">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Box ta="right">{children}</Box>
    </Group>
  );
}

export function TableCaption({ children }: { children: ReactNode }) {
  return <Text className="sl-table-caption">{children}</Text>;
}
