import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test as base } from '@playwright/test';

export const PASSWORD = process.env['LEXVAY_DEMO_PASSWORD'] ?? 'lexvay-demo';
export const DANA = 'dana.whitfield@northfield.example';
export const PRIYA = 'priya.raman@northfield.example';
export const JORDAN = 'jordan.ellis@northfield.example';

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

/**
 * Every test fails if the page asked any host other than localhost for anything: fonts, icons and
 * scripts are bundled, and the product makes no third-party requests.
 */
export const test = base.extend<{ thirdPartyGuard: void }>({
  thirdPartyGuard: [async ({ page }, use) => {
    const foreign: string[] = [];
    page.on('request', (request) => {
      const url = new URL(request.url());
      if ((url.protocol === 'http:' || url.protocol === 'https:') && !LOCAL_HOSTS.has(url.hostname)) {
        foreign.push(request.url());
      }
    });
    await use();
    expect(foreign, 'requests to third-party hosts').toEqual([]);
  }, { auto: true }],
});
export { expect };

export async function signIn(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('navigation', { name: 'Screens' })).toBeVisible();
}

async function axe(page: Page, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  const severe = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(severe, `${screen}: ${severe.map((v) => `${v.id} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`).join('; ')}`).toEqual([]);
}

/** Zero serious or critical axe findings on the current screen, in the light theme and in the dark theme. */
export async function expectAccessible(page: Page, screen: string): Promise<void> {
  const original = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  // Colour transitions would otherwise be measured half-way between the two themes.
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; animation: none !important; }' });
  for (const theme of ['light', 'dark']) {
    await page.evaluate((t) => document.documentElement.setAttribute('data-theme', t), theme);
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await axe(page, `${screen} (${theme})`);
  }
  await page.evaluate((t) => document.documentElement.setAttribute('data-theme', t ?? 'light'), original);
}

/** Screenshots for docs/screens are taken only on request: LEXVAY_SCREENSHOTS=1. */
export const SCREENSHOTS = process.env['LEXVAY_SCREENSHOTS'] === '1';

/** Saves docs/screens/<name>-light.png and -dark.png of the current page. */
export async function shoot(page: Page, name: string): Promise<void> {
  for (const theme of ['light', 'dark']) {
    await page.evaluate((t) => document.documentElement.setAttribute('data-theme', t), theme);
    await page.mouse.move(0, 0);
    // Let PrimeNG's colour transitions finish before capturing.
    await page.waitForTimeout(600);
    await page.screenshot({ path: `../docs/screens/${name}-${theme}.png` });
  }
  await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'light'));
}

export function nav(page: Page, name: string) {
  return page.getByRole('navigation', { name: 'Screens' }).getByRole('link', { name, exact: true });
}
