import { defineConfig, devices } from '@playwright/test';

/** End-to-end tests run against the isolated, seeded stack started by scripts/e2e.sh; ports come from its slot. */
const webPort = process.env['LEXVAY_E2E_WEB_PORT'] ?? '4300';

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${webPort}`,
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: `npm start -- --port ${webPort} --proxy-config proxy.e2e.conf.mjs`,
    url: `http://localhost:${webPort}`,
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
