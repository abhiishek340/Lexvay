import { defineConfig, devices } from '@playwright/test';

/** End-to-end tests run against the isolated e2e stack started by scripts/e2e.sh (backend on :8081, seeded). */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: 'npm start -- --port 4300 --proxy-config proxy.e2e.conf.json',
    url: 'http://localhost:4300',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
