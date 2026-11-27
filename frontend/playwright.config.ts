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
  // Every spec shares one seeded stack, and a jurisdiction can be signed once. The register and records specs read the
  // California signature that review-and-sign.spec.ts records, so they run after the rest of the suite. The setup spec
  // creates a second program, after which the home screen lists programs instead of opening the only one, so it runs last.
  projects: [
    { name: 'suite', testIgnore: /(register|records|setup)\.spec\.ts$/ },
    { name: 'register', testMatch: /register\.spec\.ts$/, dependencies: ['suite'] },
    { name: 'records', testMatch: /records\.spec\.ts$/, dependencies: ['suite'] },
    { name: 'setup', testMatch: /setup\.spec\.ts$/, dependencies: ['register', 'records'] },
  ],
  webServer: {
    command: `npm start -- --port ${webPort} --proxy-config proxy.e2e.conf.mjs`,
    url: `http://localhost:${webPort}`,
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
