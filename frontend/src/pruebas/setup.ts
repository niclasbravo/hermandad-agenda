import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { servidor } from './servidor';

class ResizeObserverFalso {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as unknown as { ResizeObserver: typeof ResizeObserverFalso }).ResizeObserver = ResizeObserverFalso;

beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { servidor.resetHandlers(); cleanup(); document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'; });
afterAll(() => servidor.close());
