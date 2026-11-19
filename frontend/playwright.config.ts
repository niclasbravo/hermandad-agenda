import { defineConfig, devices } from '@playwright/test';

/** E2E contra el sistema levantado (Vite en 5173 o Nginx en 8080) con el perfil demo. */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_URL ?? 'http://localhost:5173',
    locale: 'es-CL',
    timezoneId: 'America/Santiago',
    trace: 'retain-on-failure',
    // E2E_CANAL=chrome usa el Chrome instalado en el equipo en vez del Chromium de Playwright.
    ...(process.env.E2E_CANAL ? { channel: process.env.E2E_CANAL } : {}),
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'celular', use: { ...devices['Pixel 5'], viewport: { width: 390, height: 844 } }, testMatch: /recorrido-h/ },
  ],
});
