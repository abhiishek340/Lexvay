import { readFile } from 'node:fs/promises';
import { PRIYA, expect, expectAccessible, nav, signIn, test } from './support';

/**
 * The determination register on the seeded program. California is signed "Determined not to meet" by
 * review-and-sign.spec.ts, which runs first (playwright.config.ts); nothing else in the suite signs.
 */
const OTHER_TARGETS = ['Arizona', 'Colorado', 'Texas', 'Utah'];

const CSV_HEADER = ['Determination ID', 'Program ID', 'Program', 'License type', 'Catalog year', 'Jurisdiction code', 'Jurisdiction',
  'Outcome', 'Status', 'Signed by (user ID)', 'Signed by', 'Signed at', 'Rule version ID', 'Rule version', 'Rule effective from',
  'Versions hash', 'Assessment ID', 'Course versions'].map((label) => `"${label}"`).join(',');

test('the register lists the California signature, filters, exports CSV, groups the program and prints', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await signIn(page, PRIYA);

  // A tenant-wide entry above the program's screens.
  const entry = nav(page, 'Determination register');
  await entry.click();
  await expect(entry).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('heading', { level: 1, name: 'Determination register' })).toBeVisible();

  const table = page.getByRole('table', { name: 'Determination register' });
  const rows = table.locator('tbody tr');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('California');
  await expect(rows.first()).toContainText('Determined not to meet');
  await expect(rows.first()).toContainText('Signed');
  await expect(rows.first()).toContainText('Dana Whitfield');
  await expect(rows.first()).toContainText('Demo rule set - California (fictional) 2026');
  await expect(page.getByTestId('register-count')).toContainText(/^\s*1\s*Signed determination\s*$/);

  // The map of signed states: California in its outcome, its card opening the evidence report.
  const map = page.getByRole('group', { name: 'Signed states' });
  await expect(map.getByRole('button')).toHaveCount(1);
  const california = map.getByRole('button', { name: 'California, determined not to meet' });
  await california.focus();
  const card = page.getByRole('dialog', { name: 'California' });
  await expect(card.getByRole('link', { name: 'View report' })).toHaveAttribute('href', /\/programs\/[0-9a-f-]+\/report\?jurisdiction=CA$/);
  await expectAccessible(page, 'Determination register with a map card');
  await page.keyboard.press('Escape');
  await expect(card).toBeHidden();
  await page.getByRole('button', { name: 'About the register' }).click();
  await expect(page.getByRole('dialog', { name: 'About the register' })).toContainText('exactly as it was signed');
  await page.keyboard.press('Escape');
  await expectAccessible(page, 'Determination register');

  // The outcome filter uses the signing labels; a filter that matches nothing says so.
  const outcome = page.getByRole('combobox', { name: 'Outcome' });
  await outcome.click();
  await page.getByRole('option', { name: 'Determined to meet', exact: true }).click();
  await expect(table).toContainText('No signed determination matches these filters.');
  await expect(map.getByRole('button')).toHaveCount(0);
  await expect(table.getByRole('button', { name: 'Clear filters' })).toBeVisible();
  await outcome.click();
  await page.getByRole('option', { name: 'Determined not to meet', exact: true }).click();
  await expect(rows).toHaveCount(1);
  await expect(page.getByRole('link', { name: 'Download CSV' })).toHaveAttribute('href', '/api/v1/determinations/register.csv?outcome=DOES_NOT_MEET');

  // The versions hash is copied whole.
  await rows.first().getByRole('button', { name: 'Copy versions hash' }).click();
  await expect(page.locator('.p-toast')).toContainText('Copied');
  const hash = await page.evaluate(() => navigator.clipboard.readText());
  expect(hash).toMatch(/^[0-9a-f]{64}$/);
  await expect(rows.first()).toContainText(hash.slice(0, 12));

  // The CSV carries the demo notice, the same filter, the labelled header and the California row.
  const downloading = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Download CSV' }).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toMatch(/^lexvay-determination-register-\d{4}-\d{2}-\d{2}\.csv$/);
  const csv = await readFile(await download.path(), 'utf8');
  const [notice, header, ...records] = csv.split('\r\n');
  expect(notice, 'the seeded tenant is a DEMO tenant, so its register says the data is fictional').toBe('"Fictional demo data"');
  expect(header).toBe(CSV_HEADER);
  expect(records).toHaveLength(2);
  expect(records[1]).toBe('');
  expect(records[0]).toContain('"CA","California","Determined not to meet","Signed"');
  expect(records[0]).toContain(`"${hash}"`);
  expect(records[0]).toContain('"Dana Whitfield"');

  // By program: California under the list it was signed into, every other target without a current signature.
  await page.getByRole('tab', { name: 'By program' }).click();
  const toMeet = page.getByRole('region', { name: 'Determined to meet', exact: true });
  const notToMeet = page.getByRole('region', { name: 'Determined not to meet', exact: true });
  const none = page.getByRole('region', { name: 'No determination made', exact: true });
  await expect(notToMeet.getByRole('listitem')).toHaveCount(1);
  await expect(notToMeet).toContainText('California');
  await expect(notToMeet).toContainText('Dana Whitfield');
  await expect(notToMeet.getByRole('img', { name: /^Signed by Dana Whitfield on .+: determined not to meet$/ })).toBeVisible();
  await expect(toMeet).toContainText('No target jurisdiction has a current signed determination to meet.');
  await expect(none.getByRole('listitem')).toHaveCount(OTHER_TARGETS.length);
  for (const [i, name] of OTHER_TARGETS.entries()) {
    const row = none.getByRole('listitem').nth(i);
    await expect(row).toContainText(name);
    await expect(row).toContainText('No current signed determination');
  }
  await page.getByRole('button', { name: 'About these lists' }).click();
  await expect(page.getByRole('dialog', { name: 'About these lists' })).toContainText('The institution\'s published disclosure remains its own responsibility.');
  await page.keyboard.press('Escape');
  await expectAccessible(page, 'Determination register, by program');

  // Paper: no shell, no controls; a header names the tenant, the program and the time; the fictional notice stays.
  await page.emulateMedia({ media: 'print' });
  await expect(page.getByRole('navigation', { name: 'Screens' })).toBeHidden();
  await expect(page.locator('.lx-topbar')).toBeHidden();
  await expect(page.getByRole('button', { name: 'Print' })).toBeHidden();
  await expect(page.getByRole('tab', { name: 'By program' })).toBeHidden();
  const printHeader = page.getByTestId('print-header');
  await expect(printHeader).toBeVisible();
  await expect(printHeader).toContainText('Northfield University');
  await expect(printHeader).toContainText('M.S. Clinical Mental Health Counseling');
  await expect(printHeader).toContainText('Generated');
  await expect(page.getByRole('complementary', { name: 'Fictional data notice' })).toBeVisible();
  // Paper keeps the explanation the screen holds behind the InfoTip.
  await expect(page.getByText('These lists show signed records only.')).toBeVisible();
  const ink = await page.evaluate(() => ({
    body: getComputedStyle(document.body).backgroundColor,
    heading: getComputedStyle(document.querySelector('main h1')!).color,
  }));
  expect(ink).toEqual({ body: 'rgb(255, 255, 255)', heading: 'rgb(0, 0, 0)' });
  await page.emulateMedia({ media: 'screen' });

  // A register row opens the program's evidence report for that jurisdiction.
  await page.getByRole('tab', { name: 'Register' }).click();
  await expect(page.getByRole('group', { name: 'Signed states' })).toBeVisible();
  await rows.first().locator('td').first().click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/report\?jurisdiction=CA$/);
  await expect(page.getByTestId('stamp')).toContainText('Determined not to meet');
});

test('the register is accessible in both themes at 400 px and fits without horizontal scroll', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  await page.goto('/register');
  await expect(page.getByRole('table', { name: 'Determination register' }).locator('tbody tr')).toHaveCount(1);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Determination register at 400 px');
  await page.getByRole('tab', { name: 'By program' }).click();
  await expect(page.getByRole('region', { name: 'No determination made', exact: true })).toBeVisible();
  await expectAccessible(page, 'Determination register, by program, at 400 px');
});
