import { DANA, PRIYA, expect, expectAccessible, nav, signIn, test } from './support';

test('Dana Whitfield reviews every California requirement, signs, and sees the audit entries', async ({ page }) => {
  await signIn(page, DANA);

  // Program overview with the permanent Demo data pill; its InfoTip holds the full notice.
  await expect(page.getByRole('heading', { level: 1, name: 'M.S. Clinical Mental Health Counseling' })).toBeVisible();
  await expect(page.getByRole('banner').getByText('Demo data', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'About demo data' }).click();
  await expect(page.getByRole('dialog', { name: 'About demo data' })).toContainText('invented for this prototype');
  await page.keyboard.press('Escape');
  await expect(page.locator('.lx-figures')).toContainText('Jurisdictions');
  await expect(page.getByRole('table', { name: 'Jurisdictions' })).toContainText('California');
  await expectAccessible(page, 'Program overview');

  // Review, California.
  await nav(page, 'Review').click();
  await expect(nav(page, 'Review')).toHaveAttribute('aria-current', 'page');
  await page.getByRole('tab', { name: 'CA', exact: true }).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^CA-/);
  const progress = page.getByTestId('progress');
  await expect(progress).toHaveText(/of 8 reviewed/);
  await expectAccessible(page, 'Review');

  for (let done = 0; done < 8; done++) {
    await expect(progress).toHaveText(`${done} of 8 reviewed`);
    const finding = (await page.getByTestId('proposed-finding').textContent()) ?? '';
    const ref = (await page.getByTestId('requirement-heading').getAttribute('data-ref')) ?? '';
    if (finding.includes('Evidence missing')) {
      // A gap without a note is refused inline, not with a browser alert.
      await page.getByRole('button', { name: 'Confirm gap' }).click();
      await expect(page.getByText('Confirming a gap requires a note')).toBeVisible();
      await page.getByLabel('Note').fill('No telehealth content appears anywhere in the curriculum documents.');
      await page.getByRole('button', { name: 'Confirm gap' }).click();
    } else if (finding.includes('Ambiguous')) {
      await page.getByLabel('Note').fill('Only an elective covers this; please send the required-course coverage.');
      await page.getByRole('button', { name: 'Request evidence from program director' }).click();
    } else {
      await page.getByRole('button', { name: 'Accept evidence' }).click();
    }
    await expect(page.getByTestId('message')).toContainText(`${ref}:`);
  }
  await expect(progress).toHaveText('8 of 8 reviewed');

  // Sign with the dialog's three outcomes; Sign stays disabled until one is chosen.
  const signButton = page.getByRole('button', { name: 'Sign determination' });
  await expect(signButton).toBeEnabled();
  await signButton.click();
  const dialog = page.getByRole('dialog', { name: 'Sign determination for CA' });
  await expect(dialog.getByText('Determined to meet', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Determined not to meet', { exact: true })).toBeVisible();
  await expect(dialog.getByText('No determination made', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Sign', exact: true })).toBeDisabled();
  await expectAccessible(page, 'Signing dialog');
  await dialog.getByLabel('Determined not to meet').check();
  await dialog.getByRole('button', { name: 'Sign', exact: true }).click();
  await expect(page.getByTestId('message')).toContainText('Determination for CA signed by Dana Whitfield');
  await expect(page.getByTestId('sign-bar')).toContainText('Determined not to meet');

  // Other screens render and pass the accessibility check.
  await nav(page, 'Changes').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Changes' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Change events' })).toBeVisible();
  await expectAccessible(page, 'Changes');

  await nav(page, 'Evidence report').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Evidence report' })).toBeVisible();
  await expect(page.getByTestId('stamp')).toContainText('Draft. Not a determination.');
  await page.getByRole('combobox', { name: 'Jurisdiction' }).click();
  await page.getByRole('option', { name: 'California (CA)' }).click();
  await expect(page.getByTestId('stamp')).toContainText('Signed determination');
  await expect(page.getByTestId('stamp')).toContainText('Determined not to meet');
  await expect(page.getByRole('table', { name: 'Requirements and evidence' })).toBeVisible();
  await expectAccessible(page, 'Evidence report');

  // Audit trail names the people and words the details.
  await nav(page, 'Audit trail').click();
  const audit = page.getByRole('table', { name: 'Audit trail' });
  await expect(audit).toContainText('Determination signed');
  await expect(audit).toContainText('Review decision recorded');
  await expect(audit).toContainText('Outcome: Determined not to meet');
  await expect(audit).toContainText('Dana Whitfield');
  await expectAccessible(page, 'Audit trail');

  // Filtering by action narrows the table to that action.
  await page.getByRole('combobox', { name: 'Action' }).click();
  await page.getByRole('option', { name: 'Determination signed' }).click();
  await expect(audit.locator('tbody tr')).toHaveCount(1);
  await expect(audit).not.toContainText('Review decision recorded');

  // The screens never use the forbidden phrases.
  const text = (await page.locator('body').innerText()).toLowerCase();
  for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
    expect(text).not.toContain(forbidden);
  }
});

test('the review tabs are keyboard operable and the sign-in page is accessible', async ({ page }) => {
  await page.goto('/login');
  await expectAccessible(page, 'Sign in');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Enter your email and password.')).toBeVisible();
  await page.getByLabel('Email').fill(PRIYA);
  await page.getByLabel('Password').fill('wrong password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();
  await expectAccessible(page, 'Sign in with an error');

  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  // Tabs are listed alphabetically: AZ, CA, CO, TX, UT.
  await page.getByRole('tab', { name: 'AZ', exact: true }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'CA', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('End');
  await expect(page.getByRole('tab', { name: 'UT', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('button', { name: 'Sign determination' })).toBeDisabled();
  await expect(page.getByText('Only a signer can sign.')).toBeVisible();
});

test('keyboard shortcuts move through the list and record a decision', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  await page.getByRole('tab', { name: 'TX', exact: true }).click();
  const heading = page.getByTestId('requirement-heading');
  await expect(heading).toHaveAttribute('data-ref', /^TX-/);
  const progress = page.getByTestId('progress');
  await expect(progress).toHaveText(/^0 of \d+ reviewed$/);
  const first = await heading.getAttribute('data-ref');

  // Shortcuts act when focus is outside text fields: move focus to the list first.
  await page.locator('.lx-req[aria-current="true"]').focus();
  await page.keyboard.press('j');
  await expect(heading).not.toHaveAttribute('data-ref', first ?? '');
  const second = await heading.getAttribute('data-ref');
  await page.keyboard.press('k');
  await expect(heading).toHaveAttribute('data-ref', first ?? '');

  // ? opens the shortcut list; Escape closes it.
  await page.keyboard.press('?');
  const help = page.getByRole('dialog', { name: 'Keyboard shortcuts' });
  await expect(help).toContainText('Accept evidence');
  await expectAccessible(page, 'Keyboard shortcuts');
  await page.keyboard.press('Escape');
  await expect(help).toBeHidden();

  // A accepts the current requirement, announces it, and opens the next one that needs a decision.
  await page.locator('.lx-req[aria-current="true"]').focus();
  await page.keyboard.press('a');
  await expect(page.getByTestId('message')).toContainText(`${first}: Accepted.`);
  await expect(progress).toHaveText(/^1 of \d+ reviewed$/);
  await expect(heading).toHaveAttribute('data-ref', second ?? '');
  await expect(page.locator('.p-toast')).toContainText(`Accepted: ${first}`);

  // G puts focus in the note, and letters typed there are text, not shortcuts.
  await page.keyboard.press('g');
  await expect(page.getByLabel('Note')).toBeFocused();
  await page.keyboard.type('ja');
  await expect(page.getByLabel('Note')).toHaveValue('ja');
  await expect(progress).toHaveText(/^1 of \d+ reviewed$/);
});

test('at 400 px the review screen fits without horizontal page scroll', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  await page.getByRole('tab', { name: 'UT', exact: true }).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^UT-/);
  await expect(page.getByRole('button', { name: 'Accept evidence' })).toBeVisible();
  const overflow = await page.evaluate(() => {
    const main = document.querySelector('main')!;
    return { page: document.documentElement.scrollWidth - window.innerWidth, main: main.scrollWidth - main.clientWidth };
  });
  expect(overflow.page).toBeLessThanOrEqual(0);
  expect(overflow.main).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Review at 400 px');
});

test('the Changes screen lists the seeded changes and re-runs matching for the new course version', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Changes').click();
  const table = page.getByRole('table', { name: 'Change events' });
  await expect(table).toContainText('New rule version');
  await expect(table).toContainText('New course version');
  await expect(table).toContainText('COUN 625');
  await expectAccessible(page, 'Changes with events');
  await page.getByRole('button', { name: 'Re-run matching' }).click();
  await expect(page.getByTestId('message')).toContainText('handled', { timeout: 60_000 });
  await expect(table).toContainText('Handled');
});
