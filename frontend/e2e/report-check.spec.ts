import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { Page } from '@playwright/test';
import { DANA, SCREENSHOTS, allowConsoleErrors, expect, expectAccessible, goTo, shoot, signIn, stateTab, test } from './support';

/**
 * F14, checking a report file. Runs after the suite (playwright.config.ts): the report it checks is the PDF of the
 * California determination that review-and-sign.spec.ts signs.
 */

interface Pdf { name: string; mimeType: string; buffer: Buffer }

/** Downloads the signed California report from the Evidence report screen, as a person would. */
async function downloadCaliforniaReport(page: Page): Promise<Pdf> {
  await goTo(page, 'Evidence report');
  await stateTab(page, 'CA').click();
  await expect(page.getByRole('img', { name: /^Signed by Dana Whitfield on / })).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download PDF' }).click();
  const file = await download;
  return { name: file.suggestedFilename(), mimeType: 'application/pdf', buffer: readFileSync(await file.path()) };
}

/** The same file with one byte changed: its fingerprint no longer matches anything. */
function modified(pdf: Pdf): Pdf {
  const buffer = Buffer.from(pdf.buffer);
  buffer[Math.floor(buffer.length / 2)] ^= 1;
  return { ...pdf, name: 'modified.pdf', buffer };
}

const sha256 = (pdf: Pdf): string => createHash('sha256').update(pdf.buffer).digest('hex');

function dialog(page: Page) {
  return page.getByRole('dialog', { name: 'Check a file' });
}

async function openCheck(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Check a file' }).click();
  await expect(dialog(page).getByText('Drop a PDF here')).toBeVisible();
}

async function check(page: Page, file: Pdf): Promise<void> {
  await dialog(page).locator('input[type=file]').setInputFiles(file);
  await expect(dialog(page).getByTestId('check-result')).toBeVisible();
}

/** A check whose determination was reopened since: the real answer for the file, with the determination reopened. */
async function answerAsReopened(page: Page): Promise<void> {
  await page.route('**/api/v1/reports/check', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.matchDetails = { ...body.matchDetails, determinationStatus: 'REOPENED', reopenedAt: '2026-11-27T09:30:00Z' };
    await route.fulfill({ response, json: body });
  });
}

test('a downloaded report matches with its facts, a changed copy does not, and both checks are in the audit trail', async ({ page }) => {
  await signIn(page, DANA);
  const pdf = await downloadCaliforniaReport(page);

  await openCheck(page);
  await check(page, pdf);
  const result = dialog(page).getByTestId('check-result');
  await expect(result).toHaveAttribute('data-result', 'signed');
  await expect(result.getByRole('heading', { level: 3 })).toHaveText(/^Matches the report generated \d{1,2} [A-Z][a-z]+ \d{4}$/);
  await expect(result.getByRole('heading', { level: 3 })).toBeFocused();
  const facts = result.getByRole('list', { name: 'Report facts' }).getByRole('listitem');
  await expect(facts).toHaveCount(3);
  await expect(facts.nth(0)).toHaveText(/^California · /);
  await expect(facts.nth(1)).toHaveText(/^Signed by Dana Whitfield, \d{1,2} [A-Z][a-z]+ \d{4}$/);
  await expect(facts.nth(2)).toHaveText(/^Demo rule set/);
  await expect(result.locator('lx-seal')).toHaveAttribute('aria-hidden', 'true');
  await expect(result.getByTestId('check-sha')).toHaveText(sha256(pdf).slice(0, 16));
  await expect(result.getByRole('button', { name: 'Copy SHA-256' })).toBeVisible();
  await expect(result.getByRole('link', { name: 'Open report' })).toHaveAttribute('href', /\/programs\/[0-9a-f-]+\/report\?jurisdiction=CA$/);

  await dialog(page).getByRole('button', { name: 'Check another' }).click();
  await check(page, modified(pdf));
  await expect(result).toHaveAttribute('data-result', 'none');
  await expect(result.getByRole('heading', { level: 3 })).toHaveText('No report in this workspace has exactly this content');
  await expect(result).toContainText('Any change to the file, even saving it again, changes its fingerprint.');
  await expect(result.getByTestId('check-sha')).toHaveText(sha256(modified(pdf)).slice(0, 16));

  await dialog(page).getByRole('button', { name: 'Check another' }).click();
  await dialog(page).locator('input[type=file]').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('Not a report') });
  await expect(dialog(page).getByTestId('message')).toHaveText('Not a PDF file');
  // A file named .pdf that is not one reaches the server, which answers 400; Chromium logs it.
  allowConsoleErrors(page, /status of 400.*\/api\/v1\/reports\/check/);
  await dialog(page).locator('input[type=file]').setInputFiles({ name: 'renamed.pdf', mimeType: 'application/pdf', buffer: Buffer.from('Not a report') });
  await expect(dialog(page).getByTestId('message')).toHaveText('Not a PDF file');

  await check(page, pdf);
  await result.getByRole('link', { name: 'Open report' }).click();
  await expect(dialog(page)).toBeHidden();
  await expect(page).toHaveURL(/\/report\?jurisdiction=CA$/);

  await goTo(page, 'Audit trail');
  await page.getByRole('combobox', { name: 'Action' }).click();
  await page.getByRole('option', { name: 'Report file checked' }).click();
  const entries = page.getByRole('main').getByRole('listitem');
  await expect(entries.filter({ hasText: 'Dana Whitfield checked a report file: it matches' }).first()).toBeVisible();
  await expect(entries.filter({ hasText: 'Dana Whitfield checked a report file: no match' }).first()).toBeVisible();
});

for (const [width, height] of [[1440, 900], [400, 860]] as const) {
  test(`every check result passes axe at ${width} px`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await signIn(page, DANA);
    const pdf = await downloadCaliforniaReport(page);

    await openCheck(page);
    await expectAccessible(page, `Check a file at ${width} px`);
    await check(page, pdf);
    await expectAccessible(page, `Check: match at ${width} px`);
    await dialog(page).getByRole('button', { name: 'Check another' }).click();
    await check(page, modified(pdf));
    await expectAccessible(page, `Check: no match at ${width} px`);
    await dialog(page).getByRole('button', { name: 'Check another' }).click();
    await answerAsReopened(page);
    await check(page, pdf);
    await expect(dialog(page).getByTestId('check-result')).toHaveAttribute('data-result', 'reopened');
    await expect(dialog(page).getByRole('heading', { level: 3 })).toHaveText('Matches, but this determination was reopened on 27 November 2026');
    await expect(dialog(page).locator('lx-seal')).toHaveCount(0);
    await expectAccessible(page, `Check: reopened at ${width} px`);
  });
}

test('screenshots of the report check at 1440 and 400 px', async ({ page }) => {
  test.skip(!SCREENSHOTS, 'screenshots are taken only on request');
  await signIn(page, DANA);
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    const pdf = await downloadCaliforniaReport(page);
    await openCheck(page);
    await shoot(page, `f14-drop-${width}`);
    await check(page, pdf);
    await shoot(page, `f14-match-${width}`);
    await dialog(page).getByRole('button', { name: 'Check another' }).click();
    await check(page, modified(pdf));
    await shoot(page, `f14-no-match-${width}`);
    await dialog(page).getByRole('button', { name: 'Check another' }).click();
    await answerAsReopened(page);
    await check(page, pdf);
    await shoot(page, `f14-reopened-${width}`);
    await page.unroute('**/api/v1/reports/check');
    await dialog(page).getByRole('button', { name: 'Close', exact: true }).click();
  }
});
