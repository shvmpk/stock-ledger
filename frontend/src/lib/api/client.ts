import { ApiError, UNAUTHORIZED_EVENT, networkError } from './errors';
import { handlePasswordChangeRequired } from './passwordChangeRedirect';
import type { ApiEnvelope, PaginationMeta, ProblemDetail } from './types';

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1';

export interface ApiResult<T> {
  data: T;
  pagination?: PaginationMeta;
}

export type QueryValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | ReadonlyArray<string | number>;

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  query?: Record<string, QueryValue>;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const base = API_BASE.replace(/\/$/, '');
  const suffix = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${base}${suffix}`, window.location.origin);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      const values = Array.isArray(value) ? value : [value];
      for (const entry of values) {
        if (entry !== undefined && entry !== null && entry !== '') {
          url.searchParams.append(key, String(entry));
        }
      }
    }
  }
  return url.toString();
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

function shouldBroadcastUnauthorized(path: string): boolean {
  return !path.startsWith('/auth/login') && !path.startsWith('/auth/me');
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const { body, query, headers, ...rest } = options;
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
  const requestHeaders = new Headers(headers);
  if (body !== undefined && !isFormData && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json');
  }
  if (!requestHeaders.has('Accept')) {
    requestHeaders.set('Accept', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      ...rest,
      credentials: 'include',
      headers: requestHeaders,
      body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw networkError();
  }

  if (response.status === 401 && shouldBroadcastUnauthorized(path)) {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }

  if (!response.ok) {
    const problem = await readProblem(response);
    if (response.status === 403 && problem?.code === 'PASSWORD_CHANGE_REQUIRED') {
      handlePasswordChangeRequired();
    }
    throw new ApiError(response.status, problem);
  }

  if (response.status === 204) {
    return { data: undefined as T };
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  return { data: envelope.data, pagination: envelope.pagination };
}

export async function apiGet<T>(path: string, query?: RequestOptions['query']): Promise<T> {
  return (await apiRequest<T>(path, { method: 'GET', query })).data;
}

export async function apiGetPage<T>(
  path: string,
  query?: RequestOptions['query'],
): Promise<ApiResult<T>> {
  return apiRequest<T>(path, { method: 'GET', query });
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  headers?: HeadersInit,
): Promise<T> {
  return (await apiRequest<T>(path, { method: 'POST', body, headers })).data;
}

export async function apiPut<T>(
  path: string,
  body?: unknown,
  headers?: HeadersInit,
): Promise<T> {
  return (await apiRequest<T>(path, { method: 'PUT', body, headers })).data;
}

export async function apiPatch<T>(
  path: string,
  body?: unknown,
  headers?: HeadersInit,
): Promise<T> {
  return (await apiRequest<T>(path, { method: 'PATCH', body, headers })).data;
}

export async function apiDelete<T>(path: string): Promise<T> {
  return (await apiRequest<T>(path, { method: 'DELETE' })).data;
}

export function parseFilename(contentDisposition: string | null, fallback: string): string {
  if (!contentDisposition) return fallback;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1].replace(/^"|"$/g, ''));
    } catch {
      return fallback;
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(contentDisposition);
  return plain?.[1] ?? fallback;
}

export async function downloadCsv(path: string, fallbackFilename: string): Promise<string> {
  const response = await fetch(buildUrl(path), {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'text/csv' },
  });

  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }
  if (!response.ok) {
    const problem = await readProblem(response);
    throw new ApiError(response.status, problem);
  }

  const blob = await response.blob();
  const filename = parseFilename(response.headers.get('Content-Disposition'), fallbackFilename);
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
  return filename;
}
