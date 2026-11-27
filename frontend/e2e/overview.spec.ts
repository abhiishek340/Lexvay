import { DANA, PRIYA, expect, expectAccessible, goTo, signIn, test } from './support';

/**
 * The program overview (UI2-1): the map, the three numbers, "Needs you" and the state cards. The seeded program
 * targets Arizona, California, Colorado, Texas and Utah; nothing here decides or signs, so the other specs see the
 * seed as it was. Statuses depend on what ran before, so the action names are matched by pattern.
 */
const STAGE_ACTION = /^(Review|Sign|View report)$/;

test('the map moves between target states with the keyboard, shows each card, and Enter runs its action', async ({ page }) => {
  await signIn(page, DANA);
  const map = page.getByRole('group', { name: 'Target states' });
  await expect(map.getByRole('button')).toHaveCount(5);
  // One tab stop: the first target in reading order.
  await expect(map.locator('button[tabindex="0"]')).toHaveCount(1);
  const california = map.getByRole('button', { name: /^California, / });
  await expect(california).toHaveAttribute('tabindex', '0');

  await california.focus();
  await expect(page.getByRole('dialog', { name: 'California' })).toBeVisible();
  await page.keyboard.press('ArrowRight');
  await expect(map.getByRole('button', { name: /^Utah, / })).toBeFocused();
  const utah = page.getByRole('dialog', { name: 'Utah' });
  await expect(utah).toBeVisible();
  await expect(utah.getByRole('link')).toHaveText(STAGE_ACTION);
  await page.keyboard.press('End');
  await expect(map.getByRole('button', { name: /^Texas, \d+ of \d+ reviewed, / })).toBeFocused();
  await page.keyboard.press('Home');
  await expect(california).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'California' })).toBeHidden();

  await page.keyboard.press('End');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/(review|report)\?jurisdiction=TX$/);
});

test('a tile card\'s action opens the state, and the overview with an open card is accessible', async ({ page }) => {
  await signIn(page, DANA);
  const arizona = page.getByRole('group', { name: 'Target states' }).getByRole('button', { name: /^Arizona, / });
  const card = page.getByRole('dialog', { name: 'Arizona' });
  // Focus keeps the card open while axe runs (the check parks the pointer, which would close a hover card).
  await arizona.focus();
  await expect(card.getByRole('img', { name: /of \d+ reviewed$/ })).toBeVisible();
  await expectAccessible(page, 'Overview with a map card');
  await arizona.blur();
  await expect(card).toBeHidden();

  // The pointer travels from the tile into its card and clicks the one action.
  await arizona.hover();
  await expect(card).toBeVisible();
  await card.getByRole('link').hover();
  await card.getByRole('link').click();
  await expect(page).toHaveURL(/\/(review|report)\?jurisdiction=AZ$/);
});

test('each state card has one action, and Review lands on the state (the shared-element navigation)', async ({ page }) => {
  await signIn(page, DANA);
  const cards = page.locator('[data-testid^="state-card-"]');
  await expect(cards).toHaveCount(5);
  for (const card of await cards.all()) {
    await expect(card.getByRole('link')).toHaveCount(1);
    await expect(card.getByRole('link')).toHaveText(STAGE_ACTION);
    await expect(card.getByRole('img')).toHaveAttribute('aria-label', /evidence found, \d+ ambiguous, \d+ evidence missing|Not assessed/);
  }
  const texas = page.getByTestId('state-card-TX');
  await expect(texas).toContainText('Texas');
  await expect(texas).toContainText(/\d+ to review/);
  await texas.getByRole('link', { name: 'Review, Texas' }).click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/review\?jurisdiction=TX$/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);

  // Back on the overview, the hero states the same numbers the API does.
  await goTo(page, 'Program overview');
  const programId = new URL(page.url()).pathname.split('/')[2];
  const overview = await (await page.request.get(`/api/v1/programs/${programId}/overview`)).json();
  const summaries = overview.assessment.jurisdictions as { total: number; decided: number }[];
  const toReview = summaries.reduce((n, s) => n + s.total - s.decided, 0);
  const hero = page.getByRole('region', { name: 'Program at a glance' });
  await expect(hero.locator('lx-stat').first()).toContainText(new RegExp(`(^|\\D)${toReview}\\s*To review`));
  await expect(hero.locator('lx-stat').nth(2)).toContainText('of 5');
});

test('"Needs you" lists at most three items of this program, each with one action that opens its screen', async ({ page }) => {
  await signIn(page, DANA);
  const needs = page.getByRole('region', { name: 'Needs you' });
  await expect(needs).toBeVisible();
  const rows = needs.getByRole('listitem');
  expect(await rows.count()).toBeGreaterThan(0);
  expect(await rows.count()).toBeLessThanOrEqual(3);
  for (const row of await rows.all()) {
    await expect(row.locator('a, button')).toHaveCount(1);
  }
  const first = rows.first().getByRole('link');
  const label = (await first.getAttribute('aria-label')) ?? '';
  expect(label).toMatch(/^(Sign|View changes|View requests|Review|Review rules|Open rules): /);
  await first.click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]+\/(review|changes|requests|rules)/);
});

test('a reviewer is never offered Sign, and under reduced motion the numbers show their final value at once', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await signIn(page, PRIYA);
  await expect(page.getByRole('heading', { level: 1, name: 'M.S. Clinical Mental Health Counseling' })).toBeVisible();
  await expect(page.locator('[data-testid^="state-card-"]')).toHaveCount(5);
  await expect(page.locator('[data-testid^="state-card-"]').getByRole('link', { name: /^Sign, / })).toHaveCount(0);
  const percent = page.getByRole('region', { name: 'Program at a glance' }).locator('lx-stat').nth(1).locator('.lx-number');
  const first = await percent.textContent();
  expect(first).toMatch(/^\d+%$/);
  await page.waitForTimeout(700);
  expect(await percent.textContent()).toBe(first);
});

test('the overview is accessible in both themes at 1440 and 400 px without horizontal scroll', async ({ page }) => {
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await signIn(page, DANA);
    await expect(page.getByRole('group', { name: 'Target states' })).toBeVisible();
    await expect(page.locator('[data-testid^="state-card-"]')).toHaveCount(5);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    await page.getByRole('button', { name: 'About these figures' }).click();
    await expect(page.getByRole('dialog', { name: 'About these figures' })).toContainText('only a signer signs');
    await page.keyboard.press('Escape');
    await expectAccessible(page, `Overview at ${width} px`);
    await page.context().clearCookies();
  }
});
