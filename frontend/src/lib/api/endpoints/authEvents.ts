import { apiGetPage, type ApiResult } from '../client';
import type { AuthEvent, AuthEventType } from '../types';

export interface AuthEventSearchParams {
  page?: number;
  size?: number;
  eventType?: AuthEventType[];
  actorEmail?: string;
  from?: string;
  to?: string;
}

export function listAuthEvents(
  params: AuthEventSearchParams = {},
): Promise<ApiResult<AuthEvent[]>> {
  return apiGetPage<AuthEvent[]>('/admin/auth-events', {
    page: params.page,
    size: params.size,
    eventType: params.eventType,
    actorEmail: params.actorEmail,
    from: params.from,
    to: params.to,
  });
}
