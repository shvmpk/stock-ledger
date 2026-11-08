import { useState } from 'react';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { Badge, Button, Tabs } from '@mantine/core';
import { IconFileUpload, IconPlus } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getOrderSummary } from '../../lib/api/endpoints/orders';
import type { Order } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ManualOrderModal } from './ManualOrderModal';
import { CsvImportModal } from './CsvImportModal';

const QUEUES = ['new', 'waiting', 'ready', 'shipped', 'returns'] as const;
type OrderQueue = (typeof QUEUES)[number];

function isOrderQueue(value: string | undefined): value is OrderQueue {
  return !!value && (QUEUES as readonly string[]).includes(value);
}

export function OrdersLayout() {
  const navigate = useNavigate();
  const { queue } = useParams();
  const active = isOrderQueue(queue) ? queue : 'new';
  const [manualOpen, setManualOpen] = useState(false);
  const [csvOpen, setCsvOpen] = useState(false);

  const summaryQuery = useQuery({ queryKey: queryKeys.orderSummary, queryFn: getOrderSummary });
  const count = (status: string) =>
    summaryQuery.data?.find((item) => item.status === status)?.count ?? 0;

  const handleCreated = (order: Order) => {
    setManualOpen(false);
    navigate(`/orders/new?order=${order.id}`);
  };

  return (
    <>
      <PageHeader
        title="Orders"
        description="One queue per operational decision: reserve, pack, dispatch, or resolve a return."
        actions={
          <>
            <Button
              variant="default"
              leftSection={<IconFileUpload size={16} />}
              onClick={() => setCsvOpen(true)}
            >
              Import CSV
            </Button>
            <Button leftSection={<IconPlus size={16} />} onClick={() => setManualOpen(true)}>
              Manual order
            </Button>
          </>
        }
      />

      <Tabs
        value={active}
        onChange={(value) => {
          if (value) navigate(`/orders/${value}`);
        }}
        variant="default"
        mb="md"
      >
        <Tabs.List>
          <Tabs.Tab value="new" rightSection={<CountBadge value={count('PENDING')} />}>
            New
          </Tabs.Tab>
          <Tabs.Tab
            value="waiting"
            rightSection={<CountBadge value={count('WAITING_FOR_STOCK')} color="amber" />}
          >
            Waiting for stock
          </Tabs.Tab>
          <Tabs.Tab value="ready" rightSection={<CountBadge value={count('READY_TO_SHIP')} />}>
            Ready to ship
          </Tabs.Tab>
          <Tabs.Tab value="shipped" rightSection={<CountBadge value={count('SHIPPED')} />}>
            Shipped
          </Tabs.Tab>
          <Tabs.Tab
            value="returns"
            rightSection={
              <CountBadge
                value={
                  count('RETURNED') + count('PARTIALLY_RETURNED') + count('CANCELLED')
                }
                color="gray"
              />
            }
          >
            Returns &amp; cancellations
          </Tabs.Tab>
        </Tabs.List>
      </Tabs>

      <Outlet />

      <ManualOrderModal
        opened={manualOpen}
        onClose={() => setManualOpen(false)}
        onCreated={handleCreated}
      />
      <CsvImportModal
        opened={csvOpen}
        onClose={() => setCsvOpen(false)}
        onImported={() => {
          setCsvOpen(false);
          navigate('/orders/new');
        }}
      />
    </>
  );
}

function CountBadge({ value, color = 'emerald' }: { value: number; color?: string }) {
  if (value <= 0) return null;
  return (
    <Badge size="xs" variant="light" color={color} className="sl-tabular">
      {value}
    </Badge>
  );
}
