import { readFileSync } from 'node:fs';
import { Page } from '@playwright/test';
import { ADMIN, DANA, expect, expectAccessible, goTo, nav, signIn, test } from './support';

/**
 * The record screens of UI2-3: the audit trail's filter chips and CSV export, and the evidence report's PDF. Runs after
 * the suite (playwright.config.ts), because the signed report and the signing entry need California signed by the
 * review suite.
 */

async function noHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => {
    const main = document.querySelector('main')!;
    return { page: document.documentElement.scrollWidth - window.innerWidth, main: main.scrollWidth - main.clientWidth };
  });
  expect(overflow.page).toBeLessThanOrEqual(0);
  expect(overflow.main).toBeLessThanOrEqual(0);
}

test('the audit filter chips ask the server, details open in place, and the administrator downloads the same entries as CSV', async ({ page }) => {
  await signIn(page, ADMIN);
  await goTo(page, 'Audit trail');
  const entries = page.getByRole('main').getByRole('listitem');
  await expect(entries.nth(1)).toBeVisible();

  const asked: string[] = [];
  page.on('request', (request) => { if (new URL(request.url()).pathname === '/api/v1/audit') { asked.push(request.url()); } });
  await page.getByRole('combobox', { name: 'Person' }).click();
  await page.getByRole('option', { name: 'Dana Whitfield' }).click();
  await expect.poll(() => asked.at(-1) ?? '').toMatch(/[?&]userId=[0-9a-f-]{36}/);
  await page.getByRole('combobox', { name: 'Action' }).click();
  await page.getByRole('option', { name: 'Determination signed' }).click();
  await expect.poll(() => asked.at(-1) ?? '').toContain('action=DETERMINATION_SIGNED');
  await expect(entries).toHaveCount(1);
  await expect(entries).toContainText('Dana Whitfield signed California: determined not to meet');
  await expect(page.getByTestId('message')).toHaveText('1 entry');

  const entry = entries.getByRole('button');
  await expect(entry).toHaveAttribute('aria-expanded', 'false');
  await entry.click();
  await expect(entry).toHaveAttribute('aria-expanded', 'true');
  await expect(entries).toContainText('Versions hash');
  await expectAccessible(page, 'Audit trail, filtered, details open');

  const download = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Download CSV' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toMatch(/^lexvay-audit-\d{4}-\d{2}-\d{2}\.csv$/);
  const [notice, header, ...rows] = readFileSync(await file.path(), 'utf8').trim().split('\r\n');
  expect(notice, 'the seeded tenant is a DEMO tenant, so its audit file says the data is fictional').toBe('"Fictional demo data"');
  expect(header).toBe('"Time (UTC)","Actor","Action","Record type","Record ID","Details","Seq","Row hash","Entry ID","Actor ID"');
  expect(rows).toHaveLength(1);
  expect(rows[0]).toContain('"Dana Whitfield","DETERMINATION_SIGNED","determination"');

  await page.getByRole('button', { name: 'Clear', exact: true }).click();
  await expect(entries.nth(1)).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Action' })).toContainText('Action');
});

test('an empty filter result says so with one action that clears it', async ({ page }) => {
  await signIn(page, DANA);
  await nav(page, 'Audit trail').click();
  // People (people.spec.ts) deactivates someone, so "Access withdrawn" alone has entries; Dana never withdraws access.
  await page.getByRole('combobox', { name: 'Person' }).click();
  await page.getByRole('option', { name: 'Dana Whitfield', exact: true }).click();
  await page.getByRole('combobox', { name: 'Action' }).click();
  await page.getByRole('option', { name: 'Access withdrawn' }).click();
  await expect(page.getByText('No entries match these filters.')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible();
});

test('the signed report carries the seal and downloads its PDF; a draft carries the Draft stamp', async ({ page }) => {
  await signIn(page, DANA);
  await goTo(page, 'Evidence report');
  await expect(page.getByRole('note', { name: 'Draft. Not a determination.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download PDF' })).toBeDisabled();

  await page.getByRole('combobox', { name: 'Jurisdiction' }).click();
  await page.getByRole('option', { name: 'California (CA)' }).click();
  await expect(page).toHaveURL(/[?&]jurisdiction=CA$/);
  await expect(page.getByRole('img', { name: /^Signed by Dana Whitfield on .+: determined not to meet$/ })).toBeVisible();
  const requirements = page.getByRole('list', { name: '8 requirements' });
  await expect(requirements.getByRole('listitem')).toHaveCount(8);
  await expect(requirements.getByRole('listitem').first().getByRole('heading', { level: 4 })).toBeVisible();

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download PDF' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toMatch(/^lexvay-evidence-report-[0-9a-f-]{36}\.pdf$/);
  expect(readFileSync(await file.path()).subarray(0, 5).toString('latin1')).toBe('%PDF-');
  await expect(page.locator('.p-toast')).toContainText('PDF ready');
});

for (const [width, height] of [[1440, 900], [400, 860]] as const) {
  test(`changes, the audit trail and both report states pass axe at ${width} px without horizontal scroll`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await signIn(page, ADMIN);

    await goTo(page, 'Changes');
    const changes = page.getByRole('list', { name: 'Change events' });
    await changes.getByRole('button', { name: 'Details' }).first().click();
    await noHorizontalScroll(page);
    await expectAccessible(page, `Changes at ${width} px`);

    await goTo(page, 'Audit trail');
    await page.getByRole('main').getByRole('listitem').first().getByRole('button').click();
    await noHorizontalScroll(page);
    await expectAccessible(page, `Audit trail at ${width} px`);

    await goTo(page, 'Evidence report');
    await expect(page.getByRole('note', { name: 'Draft. Not a determination.' })).toBeVisible();
    await noHorizontalScroll(page);
    await expectAccessible(page, `Draft report at ${width} px`);
    await page.getByRole('combobox', { name: 'Jurisdiction' }).click();
    await page.getByRole('option', { name: 'California (CA)' }).click();
    await expect(page.getByRole('img', { name: /^Signed by / })).toBeVisible();
    await noHorizontalScroll(page);
    await expectAccessible(page, `Signed report at ${width} px`);
  });
}
