import { Group, Pagination, Text } from '@mantine/core';
import { formatQuantity } from '../../lib/format/quantity';

export function TablePagination({
  page,
  totalPages,
  totalElements,
  size,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onChange: (page: number) => void;
}) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <Group justify="space-between" mt="md" wrap="wrap" gap="sm">
      <Text size="xs" c="dimmed">
        Showing {formatQuantity(from)}–{formatQuantity(to)} of {formatQuantity(totalElements)}
      </Text>
      {totalPages > 1 ? (
        <Pagination
          total={totalPages}
          value={page + 1}
          onChange={(next) => onChange(next - 1)}
          size="sm"
          withEdges
          aria-label="Pagination"
        />
      ) : null}
    </Group>
  );
}
