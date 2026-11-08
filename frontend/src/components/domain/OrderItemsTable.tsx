import { Button, Table, Text } from '@mantine/core';
import { IconHelpCircle } from '@tabler/icons-react';
import type { OrderItem, Product } from '../../lib/api/types';
import { formatMinorUnits, formatMoney, lineTotal } from '../../lib/format/money';
import { formatQuantity } from '../../lib/format/quantity';

export function OrderItemsTable({
  items,
  products = [],
  onExplain,
}: {
  items: OrderItem[];
  products?: Product[];
  onExplain?: (variantId: string) => void;
}) {
  const variantBySku = new Map(
    products.flatMap((product) =>
      product.variants.map((variant) => [variant.sku, { product, variant }] as const),
    ),
  );

  return (
    <Table className="sl-data-table" captionSide="top">
      <Table.Caption className="sl-table-caption">Order items</Table.Caption>
      <Table.Thead>
        <Table.Tr>
          <Table.Th scope="col">Item</Table.Th>
          <Table.Th scope="col" ta="right">
            Qty
          </Table.Th>
          <Table.Th scope="col" ta="right">
            Unit price
          </Table.Th>
          <Table.Th scope="col" ta="right">
            Line total
          </Table.Th>
          {onExplain ? (
            <Table.Th scope="col" ta="right">
              Actions
            </Table.Th>
          ) : null}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {items.map((item) => {
          const match = variantBySku.get(item.sku);
          return (
            <Table.Tr key={item.id}>
              <Table.Th scope="row" fw={500}>
                <Text size="sm">{match?.product.name ?? 'Catalog item'}</Text>
                <Text size="xs" c="dimmed">
                  {match ? `${match.variant.optionValue} · ` : ''}
                  <span className="sl-code">{item.sku}</span>
                </Text>
              </Table.Th>
              <Table.Td ta="right" className="sl-tabular">
                {formatQuantity(item.quantity)}
              </Table.Td>
              <Table.Td ta="right" className="sl-tabular">
                {formatMoney(item.unitPrice)}
              </Table.Td>
              <Table.Td ta="right" className="sl-tabular" fw={600}>
                {formatMinorUnits(lineTotal(item.unitPrice, item.quantity))}
              </Table.Td>
              {onExplain ? (
                <Table.Td ta="right">
                  {match ? (
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      leftSection={<IconHelpCircle size={13} />}
                      onClick={() => onExplain(match.variant.id)}
                    >
                      Why this number?
                    </Button>
                  ) : (
                    <Text size="xs" c="dimmed">
                      —
                    </Text>
                  )}
                </Table.Td>
              ) : null}
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}
