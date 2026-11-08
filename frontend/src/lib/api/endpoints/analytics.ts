import { apiGet } from '../client';
import type { ShippingSummary } from '../types';

export function listShippingSummary(month?: string): Promise<ShippingSummary[]> {
  return apiGet<ShippingSummary[]>('/analytics/shipping-summary', { month });
}
