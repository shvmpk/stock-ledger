import { Skeleton, Table } from '@mantine/core';

export function TableSkeleton({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <Table aria-busy="true" aria-label="Loading table" className="sl-data-table">
      <Table.Thead>
        <Table.Tr>
          {Array.from({ length: columns }).map((_, index) => (
            <Table.Th key={index}>
              <Skeleton height={12} width="70%" />
            </Table.Th>
          ))}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <Table.Tr key={rowIndex}>
            {Array.from({ length: columns }).map((__, columnIndex) => (
              <Table.Td key={columnIndex}>
                <Skeleton height={14} width={columnIndex === 0 ? '80%' : '55%'} />
              </Table.Td>
            ))}
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}
