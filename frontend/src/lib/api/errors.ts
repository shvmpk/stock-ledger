import type {
  InvalidParam,
  ProblemDetail,
  ProblemReservationHolder,
  StockAction,
} from './types';

export const UNAUTHORIZED_EVENT = 'stockledger:unauthorized';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  readonly detail: string;
  readonly invalidParams: InvalidParam[];
  readonly type?: string;
  readonly instance?: string;
  readonly timestamp?: string;
  readonly sku?: string;
  readonly requestedQuantity?: number;
  readonly availableQuantity?: number;
  readonly onHandQuantity?: number;
  readonly reservedQuantity?: number;
  readonly reservedBy: ProblemReservationHolder[];
  readonly actions: StockAction[];

  constructor(status: number, problem: ProblemDetail | null, fallbackMessage?: string) {
    const detail = problem?.detail ?? fallbackMessage ?? 'The request could not be completed.';
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code ?? (status === 0 ? 'NETWORK_ERROR' : 'REQUEST_FAILED');
    this.title = problem?.title ?? (status === 0 ? 'Network error' : 'Request failed');
    this.detail = detail;
    this.invalidParams = problem?.invalidParams ?? [];
    this.type = problem?.type;
    this.instance = problem?.instance;
    this.timestamp = problem?.timestamp;
    this.sku = problem?.sku;
    this.requestedQuantity = problem?.requestedQuantity;
    this.availableQuantity = problem?.availableQuantity;
    this.onHandQuantity = problem?.onHandQuantity;
    this.reservedQuantity = problem?.reservedQuantity;
    this.reservedBy = problem?.reservedBy ?? [];
    this.actions = problem?.actions ?? [];
  }

  fieldError(field: string): string | undefined {
    return this.invalidParams.find((param) => param.field === field)?.message;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    return error.detail;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'Something went wrong. Please try again.';
}

export function networkError(): ApiError {
  return new ApiError(0, null, 'Cannot reach StockLedger. Check the API and try again.');
}
