import { apiGet, apiPut } from '../client';
import type {
  ReorderDetail,
  ReorderList,
  ReorderSettings,
  UpdateReorderSettingsInput,
} from '../types';

export function getReorderList(): Promise<ReorderList> {
  return apiGet<ReorderList>('/analytics/reorder');
}

export function getReorderDetail(variantId: string): Promise<ReorderDetail> {
  return apiGet<ReorderDetail>(`/analytics/reorder/${variantId}`);
}

export function getReorderSettings(): Promise<ReorderSettings> {
  return apiGet<ReorderSettings>('/settings/reorder');
}

export function updateReorderSettings(
  input: UpdateReorderSettingsInput,
): Promise<ReorderSettings> {
  return apiPut<ReorderSettings>('/settings/reorder', input);
}
