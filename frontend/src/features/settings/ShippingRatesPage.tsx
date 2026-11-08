import { useMemo, useState } from 'react';
import {
  Badge,
  Box,
  Button,
  Group,
  Loader,
  Modal,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconPencil, IconRefresh } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listShippingRates, updateShippingRate } from '../../lib/api/endpoints/shipping';
import type { Carrier, ParcelSize, ShippingRate } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { TableSkeleton } from '../../components/feedback/TableSkeleton';
import {
  formatMinorUnits,
  formatMoney,
  isValidMoney,
  parseMoneyInput,
  toMinorUnits,
} from '../../lib/format/money';
import { carrierLabels, parcelLabels } from '../../theme/tokens';
import { formatQuantity } from '../../lib/format/quantity';

const CARRIERS: Carrier[] = ['DHL', 'FEDEX', 'UPS', 'ROYAL_MAIL'];
const PARCELS: ParcelSize[] = ['SMALL', 'MEDIUM', 'LARGE'];

interface EditTarget {
  carrier: Carrier;
  parcelSize: ParcelSize;
  rate: ShippingRate | null;
}

export function ShippingRatesPage() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.shippingRates, queryFn: listShippingRates });
  const [edit, setEdit] = useState<EditTarget | null>(null);
  const [cost, setCost] = useState('');
  const [active, setActive] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const byCell = useMemo(() => {
    const map = new Map<string, ShippingRate>();
    for (const rate of query.data ?? []) map.set(`${rate.carrier}:${rate.parcelSize}`, rate);
    return map;
  }, [query.data]);

  const activeCount = (query.data ?? []).filter((rate) => rate.active).length;
  const configuredCount = query.data?.length ?? 0;
  const averageActive =
    activeCount > 0
      ? Math.round(
          (query.data ?? [])
            .filter((rate) => rate.active)
            .reduce((sum, rate) => sum + toMinorUnits(rate.cost), 0) / activeCount,
        )
      : 0;

  const openEdit = (target: EditTarget) => {
    setEdit(target);
    setCost(target.rate ? String(target.rate.cost) : '');
    setActive(target.rate?.active ?? true);
    setError(null);
  };

  const mutation = useMutation({
    mutationFn: () =>
      updateShippingRate({
        carrier: edit?.carrier as Carrier,
        parcelSize: edit?.parcelSize as ParcelSize,
        cost: parseMoneyInput(cost) as string,
        active,
      }),
    onSuccess: async (saved) => {
      notifications.show({
        title: 'Shipping rate saved',
        message: `${carrierLabels[saved.carrier]} ${parcelLabels[saved.parcelSize]} is ${saved.active ? 'active' : 'inactive'} at ${formatMoney(saved.cost)}.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      setEdit(null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.shippingRates });
    },
    onError: (caught) => setError(caught),
  });

  const save = () => {
    setError(null);
    if (!isValidMoney(cost)) {
      setError(new Error('Enter a valid non-negative amount with up to two decimals.'));
      return;
    }
    mutation.mutate();
  };

  return (
    <>
      <PageHeader
        title="Shipping rates"
        description="The owner-maintained price list used to resolve actual carrier cost at dispatch."
        actions={
          <Button
            variant="default"
            leftSection={<IconRefresh size={16} />}
            onClick={() => void query.refetch()}
            loading={query.isFetching}
          >
            Refresh
          </Button>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="Shipping rate summary">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">
            {formatQuantity(activeCount)} / {formatQuantity(CARRIERS.length * PARCELS.length)}
          </Text>
          <Text className="sl-summary-label">Active rates</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(configuredCount)}</Text>
          <Text className="sl-summary-label">Configured cells</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatMinorUnits(averageActive)}</Text>
          <Text className="sl-summary-label">Average active cost</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(CARRIERS.length)}</Text>
          <Text className="sl-summary-label">Carriers</Text>
        </Box>
      </Box>

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.isLoading ? (
        <TableSkeleton rows={4} columns={5} />
      ) : (
        <Table.ScrollContainer minWidth={820}>
          <Table className="sl-data-table" captionSide="top">
            <Table.Caption className="sl-table-caption">
              Carrier × parcel-size matrix. Every active cell can be used to resolve dispatch cost.
            </Table.Caption>
            <Table.Thead>
              <Table.Tr>
                <Table.Th scope="col">Carrier</Table.Th>
                {PARCELS.map((parcel) => (
                  <Table.Th key={parcel} scope="col">
                    {parcelLabels[parcel]}
                  </Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {CARRIERS.map((carrier) => (
                <Table.Tr key={carrier}>
                  <Table.Th scope="row" fw={600}>
                    {carrierLabels[carrier]}
                  </Table.Th>
                  {PARCELS.map((parcel) => {
                    const rate = byCell.get(`${carrier}:${parcel}`) ?? null;
                    return (
                      <Table.Td key={parcel}>
                        <Stack gap={4}>
                          <Group gap="xs" justify="space-between" wrap="nowrap">
                            <Text size="sm" fw={rate ? 700 : 400} className="sl-tabular" c={rate ? undefined : 'dimmed'}>
                              {rate ? formatMoney(rate.cost) : 'Not configured'}
                            </Text>
                            {rate ? (
                              <Badge color={rate.active ? 'green' : 'gray'} variant="light" size="xs">
                                {rate.active ? 'Active' : 'Inactive'}
                              </Badge>
                            ) : (
                              <Badge color="gray" variant="light" size="xs">
                                Missing
                              </Badge>
                            )}
                          </Group>
                          <Button
                            size="compact-xs"
                            variant="subtle"
                            leftSection={<IconPencil size={12} />}
                            onClick={() => openEdit({ carrier, parcelSize: parcel, rate })}
                          >
                            {rate ? 'Edit rate' : 'Set rate'}
                          </Button>
                        </Stack>
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}

      <Text size="xs" c="dimmed" mt="md">
        Dispatch uses the active rate for the selected carrier and parcel size. An explicit actual-cost
        override on a shipment takes precedence.
      </Text>

      <Modal
        opened={edit !== null}
        onClose={() => {
          if (!mutation.isPending) setEdit(null);
        }}
        title={edit ? `${carrierLabels[edit.carrier]} · ${parcelLabels[edit.parcelSize]}` : 'Shipping rate'}
        size="sm"
      >
        <Stack gap="md">
          <Title order={3} size="h5">
            {edit?.rate ? 'Edit shipping rate' : 'Configure shipping rate'}
          </Title>
          <TextInput
            label="Cost (GBP)"
            placeholder="7.20"
            value={cost}
            onChange={(event) => setCost(event.currentTarget.value)}
            required
          />
          <Switch
            label="Active for dispatch"
            description="Inactive rates stay in the price list but are never resolved."
            checked={active}
            onChange={(event) => setActive(event.currentTarget.checked)}
          />
          {error ? <ProblemAlert error={error} title="Could not save rate" compact /> : null}
          {mutation.isPending ? (
            <Group gap="xs">
              <Loader size="xs" />
              <Text size="xs" c="dimmed">
                Saving rate…
              </Text>
            </Group>
          ) : null}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setEdit(null)} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button onClick={save} loading={mutation.isPending}>
              Save rate
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
