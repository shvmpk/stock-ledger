import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  ThemeIcon,
} from '@mantine/core';
import { Dropzone } from '@mantine/dropzone';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconDownload,
  IconFileTypeCsv,
  IconUpload,
  IconX,
} from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { importCsv } from '../../lib/api/endpoints/orders';
import type { CsvImportResult } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { formatQuantity } from '../../lib/format/quantity';

const CSV_HEADER =
  'OrderNumber,CustomerName,CustomerEmail,ShippingAddress,ShippingCharge,SKU,Quantity,UnitPrice\n';

function downloadTemplate() {
  const blob = new Blob([CSV_HEADER], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'stockledger-order-import-template.csv';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function CsvImportModal({
  opened,
  onClose,
  onImported,
}: {
  opened: boolean;
  onClose: () => void;
  onImported: (result: CsvImportResult) => void;
}) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<CsvImportResult | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [rejectError, setRejectError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: importCsv,
    onSuccess: async (imported) => {
      setResult(imported);
      setError(null);
      notifications.show({
        title: 'CSV import finished',
        message: `${imported.importedOrders} orders imported, ${imported.waitingForStockOrders} waiting for stock, ${imported.failedRows} rows failed.`,
        color: imported.failedRows > 0 ? 'amber' : 'emerald',
        icon: <IconCheck size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['orders'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.orderSummary }),
        queryClient.invalidateQueries({ queryKey: queryKeys.products }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
      ]);
    },
    onError: (caught) => setError(caught),
  });

  const reset = () => {
    setFile(null);
    setResult(null);
    setError(null);
    setRejectError(null);
  };

  const close = () => {
    if (mutation.isPending) return;
    reset();
    onClose();
  };

  return (
    <Modal
      opened={opened}
      onClose={close}
      title="Import orders from CSV"
      size="lg"
      closeOnClickOutside={!mutation.isPending}
      closeOnEscape={!mutation.isPending}
    >
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          One row creates one order with one item. Quote fields containing commas. Order numbers must be
          unique; successful rows must not be re-uploaded.
        </Text>

        <Group justify="space-between" align="center">
          <Button
            variant="subtle"
            size="xs"
            leftSection={<IconDownload size={14} />}
            onClick={downloadTemplate}
          >
            Download CSV template
          </Button>
          <Text size="xs" c="dimmed" className="sl-code">
            {CSV_HEADER.trim()}
          </Text>
        </Group>

        {!result ? (
          <>
            <Dropzone
              onDrop={(files) => {
                setFile(files[0] ?? null);
                setResult(null);
                setError(null);
                setRejectError(null);
              }}
              onReject={() => setRejectError('Choose one .csv file no larger than 5 MB.')}
              maxSize={5 * 1024 * 1024}
              accept={['text/csv', 'application/vnd.ms-excel']}
              multiple={false}
              maxFiles={1}
              disabled={mutation.isPending}
              aria-label="Upload order CSV"
            >
              <Group justify="center" gap="xl" mih={160} style={{ pointerEvents: 'none' }}>
                <Dropzone.Accept>
                  <ThemeIcon variant="light" color="emerald" size={48} radius="sm">
                    <IconUpload size={26} />
                  </ThemeIcon>
                </Dropzone.Accept>
                <Dropzone.Reject>
                  <ThemeIcon variant="light" color="red" size={48} radius="sm">
                    <IconX size={26} />
                  </ThemeIcon>
                </Dropzone.Reject>
                <Dropzone.Idle>
                  <ThemeIcon variant="light" color="slate" size={48} radius="sm">
                    <IconFileTypeCsv size={26} />
                  </ThemeIcon>
                </Dropzone.Idle>
                <Box>
                  <Text size="sm" fw={600}>
                    Drop a CSV here or click to choose
                  </Text>
                  <Text size="xs" c="dimmed">
                    Up to 5 MB. Streaming partial-success report.
                  </Text>
                </Box>
              </Group>
            </Dropzone>

            {rejectError ? (
              <Alert color="red" variant="light" icon={<IconAlertTriangle size={16} />}>
                {rejectError}
              </Alert>
            ) : null}

            {file ? (
              <Group justify="space-between">
                <Group gap="xs">
                  <IconFileTypeCsv size={18} aria-hidden="true" />
                  <Text size="sm">{file.name}</Text>
                  <Text size="xs" c="dimmed">
                    {Math.ceil(file.size / 1024)} KB
                  </Text>
                </Group>
                <Button
                  size="xs"
                  variant="subtle"
                  color="gray"
                  leftSection={<IconX size={14} />}
                  onClick={() => setFile(null)}
                  disabled={mutation.isPending}
                >
                  Remove
                </Button>
              </Group>
            ) : null}

            {error ? <ProblemAlert error={error} title="CSV import failed" /> : null}

            <Group justify="flex-end">
              <Button variant="default" onClick={close} disabled={mutation.isPending}>
                Cancel
              </Button>
              <Button
                leftSection={<IconUpload size={16} />}
                loading={mutation.isPending}
                disabled={!file}
                onClick={() => {
                  if (!file) return;
                  setError(null);
                  mutation.mutate(file);
                }}
              >
                Import orders
              </Button>
            </Group>
          </>
        ) : (
          <>
            <Alert
              color={result.failedRows > 0 ? 'amber' : 'emerald'}
              variant="light"
              icon={result.failedRows > 0 ? <IconAlertTriangle size={18} /> : <IconCheck size={18} />}
              title="Partial-success report"
            >
              <Group gap="lg" wrap="wrap">
                <Text size="sm">
                  Rows read <Text span fw={700}>{formatQuantity(result.totalRows)}</Text>
                </Text>
                <Text size="sm">
                  Imported <Text span fw={700}>{formatQuantity(result.importedOrders)}</Text>
                </Text>
                <Text size="sm">
                  Waiting for stock{' '}
                  <Text span fw={700}>{formatQuantity(result.waitingForStockOrders)}</Text>
                </Text>
                <Text size="sm">
                  Failed <Text span fw={700}>{formatQuantity(result.failedRows)}</Text>
                </Text>
              </Group>
            </Alert>

            {result.errors.length > 0 ? (
              <Table className="sl-data-table" captionSide="top">
                <Table.Caption className="sl-table-caption">Rejected rows</Table.Caption>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th scope="col">Row</Table.Th>
                    <Table.Th scope="col">Order</Table.Th>
                    <Table.Th scope="col">SKU</Table.Th>
                    <Table.Th scope="col">Error</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {result.errors.map((row) => (
                    <Table.Tr key={`${row.rowNumber}-${row.orderNumber}-${row.sku}`}>
                      <Table.Td className="sl-tabular">{row.rowNumber}</Table.Td>
                      <Table.Td className="sl-code">{row.orderNumber ?? '—'}</Table.Td>
                      <Table.Td className="sl-code">{row.sku ?? '—'}</Table.Td>
                      <Table.Td>
                        <Text size="xs">{row.error}</Text>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            ) : (
              <Text size="sm" c="dimmed">
                Every row imported successfully.
              </Text>
            )}

            <Text size="xs" c="dimmed">
              Successful rows are already persisted. Do not upload this file again.
            </Text>

            <Group justify="space-between">
              <Button variant="subtle" onClick={reset}>
                Import another file
              </Button>
              <Button onClick={() => onImported(result)}>Done</Button>
            </Group>
          </>
        )}
      </Stack>
    </Modal>
  );
}
