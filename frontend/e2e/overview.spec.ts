import { DANA, PRIYA, expect, expectAccessible, goTo, programIdFromUrl, signIn, test } from './support';

/**
 * The program overview (UI3-0, design v3): the title row, the three figures, the States table and "Needs you". The
 * seeded program targets Arizona, California, Colorado, Texas and Utah; nothing here decides or signs, so the other
 * specs see the seed as it was. Statuses depend on what ran before, so the action names are matched by pattern.
 */
const STAGE_ACTION = /^(Review|Sign|Report)$/;

test('the States table lists every target state with its rules, progress, share found, determination and one action', async ({ page }) => {
  await signIn(page, DANA);
  const table = page.getByRole('table', { name: 'States' });
  const rows = table.locator('tbody tr');
  await expect(rows).toHaveCount(5);
  await expect(table.getByRole('columnheader')).toHaveText(['State', 'Rules', 'Progress', 'Evidence found', 'Determination', 'Next step']);
  for (const row of await rows.all()) {
    await expect(row.getByRole('link')).toHaveCount(1);
    await expect(row.getByRole('link')).toHaveText(STAGE_ACTION);
    await expect(row).toContainText(/\d+ of \d+ decided|Not assessed/);
    await expect(row).toContainText(/Signed by .+, \d+ \w{3}|Not signed|Reopened, not signed/);
  }
  const texas = page.getByTestId('state-row-TX');
  await expect(texas.getByRole('rowheader')).toHaveText('Texas');
  await texas.getByRole('link', { name: 'Review, Texas' }).click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/review\?jurisdiction=TX$/);
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);

  // Back on the overview, the figures state the same numbers the API does.
  await goTo(page, 'Program overview');
  const programId = await programIdFromUrl(page);
  const overview = await (await page.request.get(`/api/v1/programs/${programId}/overview`)).json();
  const summaries = overview.assessment.jurisdictions as { total: number; decided: number }[];
  const toReview = summaries.reduce((n, s) => n + s.total - s.decided, 0);
  const figures = page.getByRole('group', { name: 'Program at a glance' }).locator('lx-figure');
  await expect(figures).toHaveCount(3);
  await expect(figures.first()).toHaveText(`${toReview}To review`);
  await expect(figures.nth(1)).toHaveText(/^\d+%Evidence found$/);
  await expect(figures.nth(2)).toHaveText(/^\d+ of 5Signed$/);
});

test('"Needs you" lists at most five items of this program, each with one action that opens its screen', async ({ page }) => {
  await signIn(page, DANA);
  const needs = page.getByRole('region', { name: 'Needs you' });
  await expect(needs).toBeVisible();
  const rows = needs.getByRole('listitem');
  expect(await rows.count()).toBeGreaterThan(0);
  expect(await rows.count()).toBeLessThanOrEqual(5);
  for (const row of await rows.all()) {
    await expect(row.locator('a, button')).toHaveCount(1);
  }
  const first = rows.first().getByRole('link');
  const label = (await first.getAttribute('aria-label')) ?? '';
  expect(label).toMatch(/^(Sign|View changes|View requests|Review|Review rules|Open rules): /);
  await first.click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/(review|changes|requests|rules)/);
});

test('a reviewer is never offered Sign, and the one Help explains the terms', async ({ page }) => {
  await signIn(page, PRIYA);
  await expect(page.getByRole('heading', { level: 1, name: 'M.S. Clinical Mental Health Counseling' })).toBeVisible();
  await expect(page.getByTestId('state-row-CA')).toBeVisible();
  await expect(page.getByRole('main').getByRole('link', { name: /^Sign/ })).toHaveCount(0);
  await expect(page.getByRole('main').getByRole('button', { name: 'Help' })).toHaveCount(1);
  await page.getByRole('button', { name: 'Help' }).click();
  await expect(page.getByRole('dialog', { name: 'Help' })).toContainText('Signed for a state by a signer');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Help' })).toBeHidden();
});

test('the overview is accessible in both themes at 1440 and 400 px without horizontal scroll', async ({ page }) => {
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await signIn(page, DANA);
    await expect(page.getByRole('table', { name: 'States' }).locator('tbody tr')).toHaveCount(5);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    await page.getByRole('button', { name: 'Help' }).click();
    await expect(page.getByRole('dialog', { name: 'Help' })).toBeVisible();
    await expectAccessible(page, `Overview with Help at ${width} px`);
    await page.keyboard.press('Escape');
    await expectAccessible(page, `Overview at ${width} px`);
    await page.context().clearCookies();
  }
});

test('the overview keeps the v3 spacing: 40 px padding, 24 px to the figures, 40 px sections with a hairline, 40 px rows', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, DANA);
  await expect(page.getByTestId('state-row-CA')).toBeVisible();
  const box = async (selector: string) => (await page.locator(selector).first().boundingBox())!;
  const main = await box('main');
  const title = await box('main h1');
  expect(title.x - main.x).toBe(40);
  expect(title.y - main.y).toBe(40);
  const header = await box('.lx-title-row');
  const figures = await box('.lx-figures');
  expect(figures.y - (header.y + header.height)).toBe(24);
  const states = page.locator('section[aria-labelledby="states-heading"]');
  expect(await states.evaluate((el) => [getComputedStyle(el).marginTop, getComputedStyle(el).paddingTop, getComputedStyle(el).borderTopWidth]))
    .toEqual(['40px', '40px', '1px']);
  for (const row of await page.locator('.lx-states tbody tr').all()) {
    expect(Math.round((await row.boundingBox())!.height)).toBe(40);
  }
});
