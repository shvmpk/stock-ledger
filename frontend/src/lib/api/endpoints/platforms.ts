import { apiGet, apiGetPage, apiPatch, apiPost, type ApiResult } from '../client';
import type {
  CreatePlatformInput,
  PlatformCatalogEntry,
  PlatformConnection,
  PlatformConnectionEvent,
  PlatformSyncResult,
  PlatformTestResult,
  UpdatePlatformInput,
} from '../types';

export function listPlatforms(): Promise<PlatformConnection[]> {
  return apiGet<PlatformConnection[]>('/platforms');
}

export function getPlatformCatalog(): Promise<PlatformCatalogEntry[]> {
  return apiGet<PlatformCatalogEntry[]>('/platforms/catalog');
}

export function createPlatform(
  input: CreatePlatformInput,
  idempotencyKey: string,
): Promise<PlatformConnection> {
  return apiPost<PlatformConnection>('/platforms', input, {
    'Idempotency-Key': idempotencyKey,
  });
}

export function updatePlatform(
  connectionId: string,
  input: UpdatePlatformInput,
): Promise<PlatformConnection> {
  return apiPatch<PlatformConnection>(`/platforms/${connectionId}`, input);
}

export function syncPlatform(connectionId: string): Promise<PlatformSyncResult> {
  return apiPost<PlatformSyncResult>(`/platforms/${connectionId}/sync`);
}

export function testPlatform(connectionId: string): Promise<PlatformTestResult> {
  return apiPost<PlatformTestResult>(`/platforms/${connectionId}/test`);
}

export function listPlatformEvents(
  connectionId: string,
  page = 0,
  size = 20,
): Promise<ApiResult<PlatformConnectionEvent[]>> {
  return apiGetPage<PlatformConnectionEvent[]>(`/platforms/${connectionId}/events`, {
    page,
    size,
  });
}

/**
 * A fresh key for one create attempt. Reusing the key when the same form is
 * submitted again lets the server return the connection it already created
 * instead of making a duplicate.
 */
export function newIdempotencyKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  const random = Math.random().toString(36).slice(2, 12);
  return `req_${Date.now().toString(36)}_${random}`;
}
