import { Page } from '@playwright/test';
import { ADMIN, PASSWORD, PRIYA, SCREENSHOTS, expect, expectAccessible, goTo, nav, shoot, signIn, test } from './support';

/** Passwords the new people choose: long enough for the rules, never an address. */
const CHOSEN = 'Granite river lantern 42';

async function openPeople(page: Page): Promise<void> {
  await goTo(page, 'People');
  await expect(page.getByRole('heading', { name: 'People', level: 1 })).toBeVisible();
  await expect(page.getByRole('table', { name: 'People' })).toBeVisible();
}

function row(page: Page, email: string) {
  return page.getByTestId(`person-${email}`);
}

/** Adds a person as a contributor (the default role) and returns the setup link, read from the one-time view. */
async function addPerson(page: Page, name: string, email: string): Promise<string> {
  await page.getByRole('main').getByRole('button', { name: 'Add person' }).click();
  const form = page.getByRole('dialog', { name: 'Add person' });
  await form.getByLabel('Name').fill(name);
  await form.getByLabel('Email').fill(email);
  await expect(form.getByRole('combobox', { name: 'Role' })).toHaveText('Contributor');
  await form.getByRole('button', { name: 'Add person' }).click();
  const view = page.getByRole('dialog', { name: 'Setup link' });
  await expect(view.getByTestId('setup-link')).toHaveText(/\/setup#code=[A-Za-z0-9_-]{43}$/);
  return (await view.getByTestId('setup-link').textContent())!.trim();
}

async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: /^Account, / }).click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
}

/**
 * Opens the link and chooses the password. The code is in the fragment: no URL the browser sends carries it (only the
 * POST body does), and the page clears it from the address bar at once.
 */
async function choosePassword(page: Page, link: string): Promise<void> {
  const code = new URLSearchParams(new URL(link).hash.slice(1)).get('code')!;
  const sent: string[] = [];
  // What goes on the wire is the URL without its fragment (Playwright may report the fragment of a navigation).
  const record = (request: { url(): string }): void => { sent.push(request.url().split('#')[0]); };
  page.on('request', record);
  await page.goto(link);
  await expect(page.getByRole('heading', { name: 'Choose a password' })).toBeVisible();
  await expect(page).toHaveURL(/\/setup$/);
  await page.getByLabel('New password', { exact: true }).fill(CHOSEN);
  await page.getByLabel('Repeat password', { exact: true }).fill(CHOSEN);
  await page.getByRole('button', { name: 'Set password' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('.p-toast')).toContainText('Password set. Sign in.');
  page.off('request', record);
  expect(sent.some((url) => url.endsWith('/api/v1/auth/setup'))).toBe(true);
  expect(sent.filter((url) => url.includes('code=') || url.includes(code))).toEqual([]);
}

async function signInAs(page: Page, email: string, password: string): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

async function personMenu(page: Page, email: string, name: string) {
  await row(page, email).getByRole('button', { name: `Actions for ${name}` }).click();
  return page.getByRole('menu', { name: 'Person actions' });
}

test('an administrator adds a contributor, who chooses a password from the link and signs in', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await signIn(page, ADMIN);
  await openPeople(page);
  const link = await addPerson(page, 'Morgan Blake', 'morgan.blake@northfield.example');

  const view = page.getByRole('dialog', { name: 'Setup link' });
  await expect(view).toContainText('Shown once.');
  await expect(view).toContainText(/Valid for 72 hours, until \d{1,2} [A-Z][a-z]{2} \d{2}:\d{2}/);
  await view.getByRole('button', { name: 'Copy link' }).click();
  await expect(view.getByRole('button', { name: 'Copied' })).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(link);
  // The code never lands in the browser's storage.
  const code = new URLSearchParams(new URL(link).hash.slice(1)).get('code')!;
  const stored = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  expect(stored).not.toContain(code);
  await view.getByRole('button', { name: 'Done' }).click();
  await expect(view).toBeHidden();
  await expect(row(page, 'morgan.blake@northfield.example')).toContainText('Setup pending');
  await expect(row(page, 'morgan.blake@northfield.example')).toContainText('Contributor');

  await signOut(page);
  await choosePassword(page, link);
  await signInAs(page, 'morgan.blake@northfield.example', CHOSEN);
  await expect(page.getByRole('navigation', { name: 'Screens' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Account, Morgan Blake, Contributor' })).toBeVisible();
  await expect(nav(page, 'People')).toHaveCount(0);

  // The link worked once: opened again, it is refused with the one message for every bad code.
  await signOut(page);
  await page.goto(link);
  await page.getByLabel('New password', { exact: true }).fill(CHOSEN);
  await page.getByLabel('Repeat password', { exact: true }).fill(CHOSEN);
  await page.getByRole('button', { name: 'Set password' }).click();
  await expect(page.getByTestId('message')).toContainText('This setup code is not valid.');
  await expect(page.getByRole('link', { name: 'Go to sign in' })).toBeVisible();
});

test("a role change ends the person's session; deactivating and reactivating change their access", async ({ page, browser }) => {
  await signIn(page, ADMIN);
  await openPeople(page);
  const link = await addPerson(page, 'Riley Chen', 'riley.chen@northfield.example');
  await page.getByRole('dialog', { name: 'Setup link' }).getByRole('button', { name: 'Done' }).click();

  const riley = await (await browser.newContext()).newPage();
  await choosePassword(riley, link);
  await signInAs(riley, 'riley.chen@northfield.example', CHOSEN);
  await expect(riley.getByRole('navigation', { name: 'Screens' })).toBeVisible();

  await (await personMenu(page, 'riley.chen@northfield.example', 'Riley Chen')).getByRole('menuitem', { name: 'Change role' }).click();
  const confirm = page.getByRole('alertdialog', { name: 'Change role' });
  await expect(confirm).toContainText('Their current session ends.');
  await expect(confirm.getByRole('button', { name: 'Change role' })).toBeDisabled();
  await confirm.getByRole('combobox', { name: 'New role' }).click();
  await page.getByRole('option', { name: 'Reviewer' }).click();
  await confirm.getByRole('button', { name: 'Change role' }).click();
  await expect(confirm).toBeHidden();
  await expect(page.locator('.p-toast')).toContainText('Riley Chen is now a reviewer.');
  await expect(row(page, 'riley.chen@northfield.example')).toContainText('Reviewer');

  // Riley's next request is refused: the screen signs out and says why.
  await nav(riley, 'Audit trail').click();
  await expect(riley).toHaveURL(/\/login$/);
  await expect(riley.getByTestId('message')).toHaveText('Your access changed. Sign in again.');
  await signInAs(riley, 'riley.chen@northfield.example', CHOSEN);
  await expect(riley.getByRole('button', { name: 'Account, Riley Chen, Reviewer' })).toBeVisible();

  await (await personMenu(page, 'riley.chen@northfield.example', 'Riley Chen')).getByRole('menuitem', { name: 'Deactivate' }).click();
  const deactivate = page.getByRole('alertdialog', { name: 'Deactivate Riley Chen?' });
  await expect(deactivate).toContainText('Their current session ends.');
  await deactivate.getByRole('button', { name: 'Deactivate' }).click();
  await expect(row(page, 'riley.chen@northfield.example')).toContainText('Deactivated');
  await nav(riley, 'Changes').click();
  await expect(riley.getByTestId('message')).toHaveText('Your access changed. Sign in again.');
  await signInAs(riley, 'riley.chen@northfield.example', CHOSEN);
  await expect(riley.getByText('Email or password is incorrect.')).toBeVisible();

  await (await personMenu(page, 'riley.chen@northfield.example', 'Riley Chen')).getByRole('menuitem', { name: 'Reactivate' }).click();
  const reactivate = page.getByRole('alertdialog', { name: 'Reactivate Riley Chen?' });
  await expect(reactivate).toContainText('They can sign in again.');
  await reactivate.getByRole('button', { name: 'Reactivate' }).click();
  await expect(row(page, 'riley.chen@northfield.example')).toContainText('Active');
  await riley.getByRole('button', { name: 'Sign in' }).click();
  await expect(riley.getByRole('navigation', { name: 'Screens' })).toBeVisible();

  // A new setup code shows the same one-time view and replaces the earlier link.
  await (await personMenu(page, 'riley.chen@northfield.example', 'Riley Chen')).getByRole('menuitem', { name: 'New setup code' }).click();
  const view = page.getByRole('dialog', { name: 'Setup link' });
  await expect(view.getByTestId('setup-link')).toHaveText(/\/setup#code=[A-Za-z0-9_-]{43}$/);
  await expect(view).toContainText('Earlier links stop working.');
  await expect(view.getByTestId('setup-link')).not.toHaveText(link);
  await view.getByRole('button', { name: 'Done' }).click();
  await riley.context().close();
});

test("an administrator's own row offers only a new setup code; refusals stay in the dialog", async ({ page }) => {
  await signIn(page, ADMIN);
  await openPeople(page);
  const own = row(page, ADMIN);
  await expect(own).toContainText('You');
  const menu = await personMenu(page, ADMIN, 'Northfield Admin');
  await expect(menu.getByRole('menuitem')).toHaveText(['New setup code']);
  await page.keyboard.press('Escape');

  // The server's 409 sentence appears inside the confirmation, which stays open.
  await page.route('**/api/v1/users/*/role', (route) => route.fulfill({
    status: 409, contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', title: 'Conflict', status: 409, detail: 'At least one active administrator is required' }),
  }));
  await (await personMenu(page, PRIYA, 'Dr. Priya Raman')).getByRole('menuitem', { name: 'Change role' }).click();
  const confirm = page.getByRole('alertdialog', { name: 'Change role' });
  await confirm.getByRole('combobox', { name: 'New role' }).click();
  await page.getByRole('option', { name: 'Signer' }).click();
  await confirm.getByRole('button', { name: 'Change role' }).click();
  await expect(confirm.getByTestId('confirm-error')).toHaveText('At least one active administrator is required');
  await confirm.getByRole('button', { name: 'Cancel' }).click();
  await expect(confirm).toBeHidden();
  await expect(row(page, PRIYA)).toContainText('Reviewer');
});

test('filters by role and status, and searches by name or email', async ({ page }) => {
  await signIn(page, ADMIN);
  await openPeople(page);
  const table = page.getByRole('table', { name: 'People' });
  await page.getByRole('searchbox', { name: 'Search by name or email' }).fill('ellis@');
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await expect(row(page, 'jordan.ellis@northfield.example')).toBeVisible();
  await page.getByRole('searchbox', { name: 'Search by name or email' }).fill('');
  await page.getByRole('combobox', { name: 'Role' }).click();
  await page.getByRole('option', { name: 'Signer' }).click();
  await expect(row(page, 'dana.whitfield@northfield.example')).toBeVisible();
  await expect(page.getByTestId('message')).toHaveText(/^1 of \d+ people$/);
  await page.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'Setup pending' }).click();
  await expect(table).toContainText('No one matches these filters.');
  await table.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page.getByTestId('message')).toHaveText(/^\d+ people$/);
});

test('a reviewer never sees People, and the address sends them to the overview', async ({ page }) => {
  await signIn(page, PRIYA);
  await expect(nav(page, 'Determination register')).toBeVisible();
  await expect(nav(page, 'People')).toHaveCount(0);
  await page.goto('/people');
  await expect(page).toHaveURL(/\/programs\/[^/]+\/overview$/);
});

test('a busy sign-in holds the button for the seconds the server asks', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) => route.fulfill({
    status: 503, headers: { 'Retry-After': '2' }, contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503, detail: 'Sign-in is busy. Try again in a moment.' }),
  }));
  await page.goto('/login');
  await signInAs(page, ADMIN, PASSWORD);
  await expect(page.getByText('Sign-in is busy. Try again in a moment.')).toBeVisible();
  const button = page.getByRole('button', { name: /^Retry in \d s$/ });
  await expect(button).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeEnabled({ timeout: 4000 });
});

test("at 400 px an administrator's bottom bar fits, with the least used screens under More", async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, ADMIN);
  const bar = page.getByRole('navigation', { name: 'Screens' });
  expect(await bar.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(0);
  await expect(nav(page, 'People')).toBeHidden();
  await expect(nav(page, 'Audit trail')).toBeHidden();
  await expect(nav(page, 'Program setup')).toBeVisible();

  const more = bar.getByRole('button', { name: 'More screens' });
  await expect(more).toHaveAttribute('aria-haspopup', 'menu');
  await more.focus();
  await page.keyboard.press('Enter');
  const menu = page.getByRole('menu', { name: 'More screens' });
  await expect(menu.getByRole('menuitem')).toHaveText(['People', 'Evidence report', 'Audit trail']);
  await expect(more).toHaveAttribute('aria-expanded', 'true');
  await expectAccessible(page, 'More menu at 400 px');
  if (SCREENSHOTS) { await shoot(page, 'f5b-more-menu-400'); }
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/people$/);
  // The tab shows that the screen open is one of its own.
  await expect(bar.getByRole('button', { name: 'More screens, People open' })).toHaveClass(/lx-active/);
  await more.click();
  await expect(menu).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(menu).toBeHidden();
});

for (const [width, height] of [[1440, 900], [400, 860]] as const) {
  test(`People, its dialogs and the setup page pass axe at ${width} px without horizontal scroll`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await signIn(page, ADMIN);
    await openPeople(page);
    await noHorizontalScroll(page);
    await expectAccessible(page, `People at ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f5b-people-${width}`); }

    await page.getByRole('main').getByRole('button', { name: 'Add person' }).click();
    const form = page.getByRole('dialog', { name: 'Add person' });
    await form.getByLabel('Name').fill('Sam Ortiz');
    await expectAccessible(page, `Add person at ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f5b-add-person-${width}`); }
    await form.getByLabel('Email').fill(`sam.ortiz.${width}@northfield.example`);
    await form.getByRole('button', { name: 'Add person' }).click();
    const view = page.getByRole('dialog', { name: 'Setup link' });
    await expect(view.getByTestId('setup-link')).toBeVisible();
    await expectAccessible(page, `Setup link at ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f5b-setup-link-${width}`); }
    const link = (await view.getByTestId('setup-link').textContent())!.trim();
    await view.getByRole('button', { name: 'Done' }).click();

    await (await personMenu(page, PRIYA, 'Dr. Priya Raman')).getByRole('menuitem', { name: 'Change role' }).click();
    const confirm = page.getByRole('alertdialog', { name: 'Change role' });
    await expect(confirm).toContainText('Their current session ends.');
    await expectAccessible(page, `Change role at ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f5b-change-role-${width}`); }
    await confirm.getByRole('button', { name: 'Cancel' }).click();

    await signOut(page);
    await page.goto(link);
    await page.getByLabel('New password', { exact: true }).fill('Granite river');
    await expect(page.getByRole('list', { name: 'Password rules' }).getByRole('listitem')).toHaveText([
      '12 to 128 characters, met', 'Not an email address, met', 'Both fields match, not met',
    ]);
    await noHorizontalScroll(page);
    await expectAccessible(page, `Setup page at ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f5b-setup-${width}`); }
  });
}

async function noHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}
