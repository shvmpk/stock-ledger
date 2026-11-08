export const DEFAULT_LANDING_PATH = '/stock/products';
export const PASSWORD_CHANGE_PATH = '/account/password';

/**
 * Only same-origin, in-app destinations are accepted. Login and the change
 * screen itself are rejected so a returnTo can never create a loop.
 */
export function safeReturnTo(value: string | null | undefined): string {
  if (
    !value ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.startsWith(PASSWORD_CHANGE_PATH) ||
    value.startsWith('/login')
  ) {
    return DEFAULT_LANDING_PATH;
  }
  return value;
}

export function passwordChangePath(returnTo: string): string {
  return `${PASSWORD_CHANGE_PATH}?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

export function currentPathWithSearch(pathname: string, search: string): string {
  return `${pathname}${search}`;
}
