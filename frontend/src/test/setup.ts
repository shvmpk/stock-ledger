import '@testing-library/jest-dom/vitest';
import 'vitest-axe/extend-expect';
import { cleanup } from '@testing-library/react';
import { notifications } from '@mantine/notifications';
import { afterAll, afterEach, beforeAll, expect } from 'vitest';
import * as matchers from 'vitest-axe/matchers';
import { resetMockData } from './handlers';
import { server } from './server';

expect.extend(matchers);

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  notifications.clean();
  server.resetHandlers();
  resetMockData();
  localStorage.clear();
  vi.clearAllTimers();
});

afterAll(() => {
  server.close();
});

Element.prototype.scrollIntoView = () => undefined;

const originalGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = ((element: Element) =>
  originalGetComputedStyle(element)) as typeof window.getComputedStyle;

const canvasContext = {
  measureText: () => ({ width: 0 }),
  fillText: () => undefined,
  font: '',
  textBaseline: '',
};
HTMLCanvasElement.prototype.getContext = (() =>
  canvasContext) as unknown as typeof HTMLCanvasElement.prototype.getContext;

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  configurable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

if (!globalThis.ResizeObserver) {
  class ResizeObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = ResizeObserverMock;
}

if (!globalThis.IntersectionObserver) {
  class IntersectionObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
  }
  globalThis.IntersectionObserver = IntersectionObserverMock as unknown as typeof IntersectionObserver;
}

URL.createObjectURL = () => 'blob:stockledger-test';
URL.revokeObjectURL = () => undefined;
