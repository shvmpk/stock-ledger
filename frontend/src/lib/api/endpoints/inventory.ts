import { apiGet, apiGetPage, apiPost } from '../client';
import type {
  MovementFilters,
  ReceiveStockResult,
  StockCount,
  StockCountStatus,
  StockCountSummary,
  StockExplanation,
  StockMovement,
} from '../types';
import type { ApiResult } from '../client';

export function getStockExplanation(variantId: string): Promise<StockExplanation> {
  return apiGet<StockExplanation>(`/stock/variants/${variantId}/explanation`);
}

export function receiveStock(input: {
  productVariantId: string;
  quantity: number;
  notes?: string;
}): Promise<ReceiveStockResult> {
  return apiPost<ReceiveStockResult>('/stock/receive', input);
}

export function listMovements(filters: MovementFilters): Promise<ApiResult<StockMovement[]>> {
  return apiGetPage<StockMovement[]>('/stock/movements', {
    page: filters.page ?? 0,
    size: filters.size ?? 20,
    sku: filters.sku,
    reason: filters.reason,
    from: filters.from,
    to: filters.to,
  });
}

export function createStockCount(): Promise<StockCount> {
  return apiPost<StockCount>('/stock/counts');
}

export function addStockCountItem(
  sessionId: string,
  input: { productVariantId: string; countedOnHand: number; notes?: string },
): Promise<StockCount> {
  return apiPost<StockCount>(`/stock/counts/${sessionId}/items`, input);
}

export function completeStockCount(sessionId: string): Promise<StockCount> {
  return apiPost<StockCount>(`/stock/counts/${sessionId}/complete`);
}

export function cancelStockCount(sessionId: string): Promise<StockCount> {
  return apiPost<StockCount>(`/stock/counts/${sessionId}/cancel`);
}

export function listStockCounts(filters: {
  status?: StockCountStatus;
  page?: number;
  size?: number;
}): Promise<ApiResult<StockCountSummary[]>> {
  return apiGetPage<StockCountSummary[]>('/stock/counts', {
    status: filters.status,
    page: filters.page ?? 0,
    size: filters.size ?? 10,
  });
}

export function getStockCount(sessionId: string): Promise<StockCount> {
  return apiGet<StockCount>(`/stock/counts/${sessionId}`);
}
