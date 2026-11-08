import { downloadCsv } from '../client';

export type ExportKind = 'catalog' | 'orders' | 'history';

const FILENAMES: Record<ExportKind, string> = {
  catalog: 'stockledger-catalog.csv',
  orders: 'stockledger-orders.csv',
  history: 'stockledger-stock-history.csv',
};

export function exportCsv(kind: ExportKind): Promise<string> {
  return downloadCsv(`/export/${kind}`, FILENAMES[kind]);
}
