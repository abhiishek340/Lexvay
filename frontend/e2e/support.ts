import AxeBuilder from '@axe-core/playwright';
import { ConsoleMessage, Locator, expect, Page, test as base } from '@playwright/test';

export const PASSWORD = process.env['LEXVAY_DEMO_PASSWORD'] ?? 'lexvay-demo';
export const DANA = 'dana.whitfield@northfield.example';
export const PRIYA = 'priya.raman@northfield.example';
export const JORDAN = 'jordan.ellis@northfield.example';
export const ADMIN = 'admin@northfield.example';

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

const allowedConsoleErrors = new WeakMap<Page, RegExp[]>();

/**
 * Declares that this test deliberately causes a console error (Chromium logs every 4xx and 5xx response as "Failed to
 * load resource"). The pattern is matched against the message text and the URL it came from, so make it specific to
 * that response's URL or status; there is no global allowlist.
 */
export function allowConsoleErrors(page: Page, pattern: RegExp): void {
  allowedConsoleErrors.set(page, [...(allowedConsoleErrors.get(page) ?? []), pattern]);
}

const consoleErrorText = (message: ConsoleMessage): string => `${message.text()} (${message.location().url})`;

/**
 * Every test fails if the page asked any host other than localhost for anything: fonts, icons and
 * scripts are bundled, and the product makes no third-party requests.
 */
export const test = base.extend<{ thirdPartyGuard: void; iconGuard: void; consoleGuard: void }>({
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
  /**
   * Every test fails if an `ng-icon` was asked for a name no provider registers (ng-icons only warns). The unit test
   * theme/icons.spec.ts checks the sources; this checks what the screens really render.
   */
  iconGuard: [async ({ page }, use) => {
    const missing: string[] = [];
    page.on('console', (message) => {
      if (message.type() === 'warning' && message.text().includes('No icon named')) { missing.push(message.text()); }
    });
    await use();
    expect(missing, 'icons that no provider registers').toEqual([]);
  }, { auto: true }],
  /**
   * Every test fails if the browser console logged an error or the page threw one, unless the test declared it with
   * `allowConsoleErrors`. A screen that logs errors on the happy path is broken even when its assertions pass.
   */
  consoleGuard: [async ({ page }, use) => {
    const errors: string[] = [];
    page.on('console', (message) => {
      if (message.type() === 'error') { errors.push(consoleErrorText(message)); }
    });
    page.on('pageerror', (error) => errors.push(error.message));
    await use();
    const allowed = allowedConsoleErrors.get(page) ?? [];
    expect(errors.filter((e) => !allowed.some((pattern) => pattern.test(e))), 'console errors').toEqual([]);
  }, { auto: true }],
});
export { expect };

/** The id of the program whose screen is open, read once the address is a program's own (the sign-in or the program list may still be showing). */
export async function programIdFromUrl(page: Page): Promise<string> {
  await page.waitForURL(/\/programs\/[0-9a-f-]{36}\//);
  return new URL(page.url()).pathname.split('/')[2];
}

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
  // A hover tooltip fades in by script (PrimeNG), which the style above cannot stop; park the pointer where nothing
  // has one, so axe never measures a tooltip half-way through its fade.
  await page.mouse.move(0, 0);
  await page.waitForTimeout(300);
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

/** A mutating API call as the signed-in user, with the CSRF header the app would send (XSRF-TOKEN cookie). */
export async function apiPost(page: Page, url: string, body: unknown) {
  const token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  return page.request.post(url, { data: body, headers: { 'X-XSRF-TOKEN': token } });
}

/** Signs the current user out by dropping the session, then signs in as someone else. */
export async function switchUser(page: Page, email: string): Promise<void> {
  await page.context().clearCookies();
  await signIn(page, email);
}

/** The seeded program's target states: Review names its state pills by their full names. */
export const STATE: Record<string, string> = { AZ: 'Arizona', CA: 'California', CO: 'Colorado', TX: 'Texas', UT: 'Utah' };

/** A state pill in Review's header (a tab named by the state's full name). */
export function stateTab(page: Page, code: string) {
  return page.getByRole('tab', { name: STATE[code], exact: true });
}

export function nav(page: Page, name: string) {
  return page.getByRole('navigation', { name: 'Screens' }).getByRole('link', { name, exact: true });
}

/**
 * Opens a screen from the rail, or, in the bottom tab bar under 640 px where it may not fit, from the "More" menu
 * (shell tabBarOverflow).
 */
export async function goTo(page: Page, name: string): Promise<void> {
  const link = nav(page, name);
  if (await link.isVisible()) {
    await link.click();
    return;
  }
  await page.getByRole('navigation', { name: 'Screens' }).getByRole('button', { name: /^More screens/ }).click();
  await page.getByRole('menu', { name: 'More screens' }).getByRole('menuitem', { name }).click();
}

export interface Evidence { documentId: string; filename: string; pageNumber: number; charStart: number; charEnd: number; quote: string; }

/**
 * The signed-in user's program (from the URL) and, read from the API, the first requirement of a state whose
 * evidence is quoted from a syllabus. The seed syllabi have two pages, so the viewer can page through them.
 */
export async function citedRequirement(page: Page, jurisdiction: string): Promise<{ programId: string; ref: string; evidence: Evidence }> {
  const programId = await programIdFromUrl(page);
  const overview = await (await page.request.get(`/api/v1/programs/${programId}/overview`)).json();
  const view = await (await page.request.get(`/api/v1/assessments/${overview.assessment.id}/mappings?jurisdiction=${jurisdiction}`)).json();
  const mapping = (view.mappings as { requirement: { ref: string }; evidence: Evidence | null }[])
    .find((m) => m.evidence?.filename.endsWith('-syllabus.pdf'))!;
  return { programId, ref: mapping.requirement.ref, evidence: mapping.evidence! };
}

/** Opens Review on a state and selects one requirement by its ref. */
export async function openRequirement(page: Page, jurisdiction: string, ref: string): Promise<void> {
  await nav(page, 'Review').click();
  await stateTab(page, jurisdiction).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', new RegExp(`^${jurisdiction}-`));
  await page.getByRole('region', { name: 'Requirements' }).getByRole('button').filter({ hasText: new RegExp(`^${ref}(?!\\d)`) }).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', ref);
}

/**
 * Opens the avatar menu and returns it. Right after a load the popover can miss its first click (the page is still
 * settling, and the menu is a deferred chunk), so the click is repeated until the menu is there.
 */
export async function openAccountMenu(page: Page): Promise<Locator> {
  const menu = page.getByRole('dialog', { name: 'Account' });
  await expect(async () => {
    if (!(await menu.isVisible())) { await page.getByRole('button', { name: /^Account, / }).click(); }
    await expect(menu).toBeVisible({ timeout: 1000 });
  }).toPass();
  return menu;
}
