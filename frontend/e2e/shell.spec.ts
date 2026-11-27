import { DANA, PRIYA, expect, expectAccessible, nav, signIn, test } from './support';

const story = (page: import('@playwright/test').Page) => page.locator('[data-story]');

test('the sign-in story plays, stops when the person types, and is accessible in both themes at 1440 and 400 px', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/login');
  await expect(story(page)).toHaveAttribute('data-story', 'playing');
  await expectAccessible(page, 'Sign in at 1440 px');
  await page.getByLabel('Email').fill(PRIYA);
  await expect(story(page)).toHaveAttribute('data-story', 'stopped');
  // Nothing moves any more: the picture is the same a second later.
  const before = await story(page).locator('.lx-tile[data-tile]').evaluateAll((tiles) => tiles.map((t) => t.getAttribute('data-tile')).join());
  await page.waitForTimeout(1000);
  const after = await story(page).locator('.lx-tile[data-tile]').evaluateAll((tiles) => tiles.map((t) => t.getAttribute('data-tile')).join());
  expect(after).toBe(before);

  await page.setViewportSize({ width: 400, height: 860 });
  await expect(story(page)).toBeHidden();
  await expectAccessible(page, 'Sign in at 400 px');
});

test('under reduced motion the sign-in story never plays', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/login');
  await expect(story(page)).toHaveAttribute('data-story', 'off');
  // The still picture shows signed and in-review states.
  await expect(story(page).locator('.lx-tile[data-tile="MEETS"]').first()).toBeVisible();
  await expect(story(page).locator('.lx-tile[data-tile="IN_REVIEW"]').first()).toBeVisible();
});

test('the command palette opens with the shortcut and jumps to Review for a state', async ({ page }) => {
  await signIn(page, PRIYA);
  await expect(page.getByRole('heading', { level: 1, name: 'M.S. Clinical Mental Health Counseling' })).toBeVisible();
  await page.keyboard.press('Control+k');
  const palette = page.getByRole('dialog', { name: 'Search or jump to' });
  await expect(palette).toBeVisible();
  // The list takes the id the search field names through pass-through once the dialog has rendered (D-311).
  const controls = await palette.getByRole('combobox').getAttribute('aria-controls');
  await expect(page.locator(`[id="${controls}"]`)).toBeAttached();
  await expectAccessible(page, 'Command palette');
  await palette.getByRole('combobox', { name: 'Search or jump to' }).fill('texas');
  await expect(palette.getByRole('menuitem').first()).toContainText('Review Texas');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/review\?jurisdiction=TX$/);
  await expect(page.getByRole('tab', { name: 'TX', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(palette).toBeHidden();

  // The command used last comes first the next time, and the shortcuts list names the palette.
  await page.getByRole('button', { name: 'Search or jump to' }).click();
  await expect(palette.getByRole('menuitem').first()).toContainText('Review Texas');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Keyboard shortcuts' }).click();
  await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toContainText(/(Ctrl|Cmd) K\s*Search or jump to/);
});

test('the command palette jumps to a requirement by its ref', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^[A-Z]{2}-/);
  await page.keyboard.press('Control+k');
  const palette = page.getByRole('dialog', { name: 'Search or jump to' });
  await palette.getByRole('combobox', { name: 'Search or jump to' }).fill('CO-3');
  await expect(palette.getByRole('menuitem').first()).toContainText('CO-3');
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', 'CO-3');
});

test('the work queue popover lists what needs the signer, each item opening its screen', async ({ page }) => {
  await signIn(page, DANA);
  const button = page.getByRole('button', { name: /^Work queue, \d+ waiting$/ });
  await expect(button).toBeVisible();
  await button.click();
  const queue = page.getByRole('dialog', { name: 'Work queue' });
  await expect(queue.getByRole('heading', { name: 'Needs you' })).toBeVisible();
  const items = queue.getByRole('link');
  await expect(items.first()).toContainText('to review');
  expect(await items.count()).toBeLessThanOrEqual(8);
  await expectAccessible(page, 'Work queue');
  await queue.getByRole('link', { name: /to review.*Texas/ }).click();
  await expect(page).toHaveURL(/\/review\?jurisdiction=TX$/);
});

test('the theme chosen in the avatar menu persists across a reload, and the menu is accessible at 400 px', async ({ page }) => {
  await signIn(page, PRIYA);
  const html = page.locator('html');
  const account = page.getByRole('button', { name: /^Account, Dr\. Priya Raman/ });
  await account.click();
  const themes = page.getByRole('group', { name: 'Theme' });
  await themes.getByRole('button', { name: 'Dark' }).click();
  await expect(html).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.getByRole('navigation', { name: 'Screens' })).toBeVisible();
  await expect(html).toHaveAttribute('data-theme', 'dark');
  await account.click();
  await expect(themes.getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true');
  await themes.getByRole('button', { name: 'Light' }).click();
  await page.reload();
  await expect(html).toHaveAttribute('data-theme', 'light');

  await page.setViewportSize({ width: 400, height: 860 });
  await account.click();
  await expect(page.getByRole('dialog', { name: 'Account' })).toContainText('Reviewer');
  await expectAccessible(page, 'Avatar menu at 400 px');
});

test('the overview with the v2 shell is accessible in both themes at 1440 and 400 px, and the rail collapses', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, PRIYA);
  await expect(page.getByRole('table', { name: 'Jurisdictions' })).toContainText('California');
  await expectAccessible(page, 'Overview at 1440 px');
  await page.getByRole('button', { name: 'Collapse navigation' }).click();
  await expect(page.getByRole('button', { name: 'Expand navigation' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: 'Expand navigation' })).toBeVisible();
  await page.getByRole('button', { name: 'Expand navigation' }).click();

  await page.setViewportSize({ width: 400, height: 860 });
  await expect(nav(page, 'Review')).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Overview at 400 px');
});

test('signing out from the avatar menu returns to the sign-in page', async ({ page }) => {
  await signIn(page, PRIYA);
  await page.getByRole('button', { name: /^Account, / }).click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible();
});
