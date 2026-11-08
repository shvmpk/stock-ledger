import type { NoViolationsMatcherResult } from 'vitest-axe/matchers';

declare module 'vitest' {
  // Vitest's Assertion interface requires the same generic parameter for declaration merging.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface Assertion<T = any> {
    toHaveNoViolations(): NoViolationsMatcherResult;
  }

  interface AsymmetricMatchersContaining {
    toHaveNoViolations(): NoViolationsMatcherResult;
  }
}
