import { apiGet, apiGetPage, apiPost } from '../client';
import type { ApiResult } from '../client';
import type {
  CreateOrderInput,
  CsvImportResult,
  Order,
  OrderStatus,
  OrderStatusSummary,
  ProcessReturnInput,
  ReturnRecord,
  ShipOrderInput,
} from '../types';

export function listOrders(filters: {
  status?: OrderStatus;
  page?: number;
  size?: number;
}): Promise<ApiResult<Order[]>> {
  return apiGetPage<Order[]>('/orders', {
    status: filters.status,
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  });
}

export function getOrder(orderId: string): Promise<Order> {
  return apiGet<Order>(`/orders/${orderId}`);
}

export function getOrderSummary(): Promise<OrderStatusSummary[]> {
  return apiGet<OrderStatusSummary[]>('/orders/summary');
}

export function createOrder(input: CreateOrderInput): Promise<Order> {
  return apiPost<Order>('/orders', input);
}

export function importCsv(file: File): Promise<CsvImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  return apiPost<CsvImportResult>('/orders/import-csv', formData);
}

export function packOrder(orderId: string): Promise<Order> {
  return apiPost<Order>(`/orders/${orderId}/pack`);
}

export function shipOrder(orderId: string, input: ShipOrderInput): Promise<Order> {
  return apiPost<Order>(`/orders/${orderId}/ship`, input);
}

export function cancelOrder(orderId: string, reason: string): Promise<Order> {
  return apiPost<Order>(`/orders/${orderId}/cancel?reason=${encodeURIComponent(reason)}`);
}

export function listReturns(orderId: string): Promise<ReturnRecord[]> {
  return apiGet<ReturnRecord[]>(`/orders/${orderId}/returns`);
}

export function processReturn(orderId: string, input: ProcessReturnInput): Promise<ReturnRecord> {
  return apiPost<ReturnRecord>(`/orders/${orderId}/returns`, input);
}
