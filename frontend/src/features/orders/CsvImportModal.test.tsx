import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CsvImportModal } from './CsvImportModal';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

const csvFile = new File(
  [
    'OrderNumber,CustomerName,CustomerEmail,ShippingAddress,ShippingCharge,SKU,Quantity,UnitPrice\n' +
      'ORD-2026-0113,Freya Lindqvist,freya.lindqvist@example.se,Kungsgatan 14 Stockholm,8.00,HOOD-NVY-XL,1,58.00\n' +
      'ORD-2026-0114,Callum Reid,callum.reid@example.co.uk,42 Leith Walk Edinburgh,5.80,COF-ETH-1KG,1,46.00\n',
  ],
  'northwind-orders-2026-10-23.csv',
  { type: 'text/csv' },
);

describe('CsvImportModal', () => {
  it('requires a CSV file before enabling import', async () => {
    renderWithProviders(
      <CsvImportModal opened onClose={vi.fn()} onImported={vi.fn()} />,
    );

    expect(screen.getByRole('button', { name: 'Import orders' })).toBeDisabled();
    expect(screen.getByText(/Drop a CSV here or click to choose/)).toBeInTheDocument();
  });

  it('uploads a CSV and renders the partial-success report', async () => {
    const user = userEvent.setup();
    const onImported = vi.fn();
    renderWithProviders(
      <CsvImportModal opened onClose={vi.fn()} onImported={onImported} />,
    );

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, csvFile);

    expect(await screen.findByText('northwind-orders-2026-10-23.csv')).toBeInTheDocument();
    const importButton = screen.getByRole('button', { name: 'Import orders' });
    expect(importButton).toBeEnabled();
    await user.click(importButton);

    expect(await screen.findByText('Partial-success report')).toBeInTheDocument();
    expect(screen.getByText(/Rows read/)).toHaveTextContent('Rows read 3');
    expect(screen.getByText(/Imported/)).toHaveTextContent('Imported 2');
    expect(screen.getByText(/Waiting for stock/)).toHaveTextContent('Waiting for stock 1');
    expect(screen.getByText(/Failed/)).toHaveTextContent('Failed 1');
    expect(screen.getByText('Rejected rows')).toBeInTheDocument();
    expect(screen.getByText('ORD-2026-0113')).toBeInTheDocument();
    expect(screen.getByText('Quantity must be greater than zero.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Done' }));
    expect(onImported).toHaveBeenCalledWith(
      expect.objectContaining({ importedOrders: 2, waitingForStockOrders: 1, failedRows: 1 }),
    );
  });

  it('shows a structured API error when the upload is rejected', async () => {
    server.use(
      http.post('/api/v1/orders/import-csv', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/csv-import-failed',
            title: 'CSV Import Failed',
            status: 400,
            detail: 'The CSV header is missing the UnitPrice column.',
            code: 'CSV_IMPORT_FAILED',
            timestamp: '2026-10-23T09:00:00Z',
            invalidParams: [],
          },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <CsvImportModal opened onClose={vi.fn()} onImported={vi.fn()} />,
    );

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, csvFile);
    await user.click(screen.getByRole('button', { name: 'Import orders' }));

    expect(await screen.findByText('CSV import failed')).toBeInTheDocument();
    expect(screen.getByText('The CSV header is missing the UnitPrice column.')).toBeInTheDocument();
    expect(screen.getByText('CSV_IMPORT_FAILED')).toBeInTheDocument();
  });
});
