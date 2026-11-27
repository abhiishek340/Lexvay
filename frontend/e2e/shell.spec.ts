import { DANA, PRIYA, expect, expectAccessible, nav, signIn, stateTab, test } from './support';

test('sign-in is one quiet column, accessible in both themes at 1440 and 400 px', async ({ page }) => {
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await page.goto('/login');
    await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible();
    await expect(page.getByText('Fictional demo data.', { exact: true })).toBeVisible();
    // No art: the mark beside the wordmark is the page's only picture.
    await expect(page.locator('lx-auth-frame svg')).toHaveCount(1);
    const column = (await page.locator('.lx-auth-column').boundingBox())!;
    expect(column.width).toBe(Math.min(360, width - 40));
    expect(Math.round(column.x + column.width / 2)).toBe(width / 2);
    await expectAccessible(page, `Sign in at ${width} px`);
  }
});

test('a demo account fills the email for the fictional tenant and moves on to the password', async ({ page }) => {
  await page.goto('/login');
  const accounts = page.getByRole('group', { name: 'Demo accounts' });
  await expect(accounts.getByRole('button')).toHaveCount(4);
  await accounts.getByRole('button', { name: /^Signer, Dana Whitfield/ }).click();
  await expect(page.getByLabel('Email')).toHaveValue(DANA);
  await expect(page.getByLabel('Password')).toBeFocused();
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
  await expect(stateTab(page, 'TX')).toHaveAttribute('aria-selected', 'true');
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
  const search = palette.getByRole('combobox', { name: 'Search or jump to' });
  await search.fill('CO-3');
  await expect(palette.getByRole('menuitem').first()).toContainText('CO-3');
  await expect(search).toHaveAttribute('aria-activedescendant', 'req:CO-3');
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

  // PrimeNG's Popover closes itself on a window resize, and after a reload the menu is a deferred chunk: let the resize
  // event pass (it fires before the next animation frames), then open the menu until it is there.
  await page.setViewportSize({ width: 400, height: 860 });
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const menu = page.getByRole('dialog', { name: 'Account' });
  await expect(async () => {
    if (!(await menu.isVisible())) { await account.click(); }
    await expect(menu).toContainText('Reviewer', { timeout: 1000 });
  }).toPass();
  await expectAccessible(page, 'Avatar menu at 400 px');
});

test('the overview with the v3 shell is accessible in both themes at 1440 and 400 px, and the rail collapses', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, PRIYA);
  await expect(page.getByTestId('state-row-CA')).toContainText('California');
  // The rail in two groups; the active screen is marked by its rule and aria-current, counts are plain text.
  const rail = page.getByRole('navigation', { name: 'Screens' });
  await expect(rail.getByRole('list', { name: 'Workspace' })).toBeVisible();
  await expect(rail.getByRole('list', { name: 'Program' })).toBeVisible();
  await expect(nav(page, 'Program overview')).toHaveAttribute('aria-current', 'page');
  expect(await nav(page, 'Program overview').evaluate((a) => getComputedStyle(a).boxShadow)).toMatch(/inset 2px 0px 0px/);
  // Focus moves to the page title after a navigation without drawing a ring on it.
  await nav(page, 'Coverage').click();
  await nav(page, 'Program overview').click();
  const title = page.getByRole('heading', { level: 1 });
  await expect(title).toBeFocused();
  expect(await title.evaluate((h) => getComputedStyle(h).outlineStyle)).toBe('none');
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
