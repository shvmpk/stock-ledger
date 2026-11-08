import { apiGet, apiPut } from '../client';
import type { ShippingRate, UpdateShippingRateInput } from '../types';

export function listShippingRates(): Promise<ShippingRate[]> {
  return apiGet<ShippingRate[]>('/shipping/rates');
}

export function updateShippingRate(input: UpdateShippingRateInput): Promise<ShippingRate> {
  return apiPut<ShippingRate>('/shipping/rates', input);
}
