import { DANA, PRIYA, SCREENSHOTS, expect, expectAccessible, nav, shoot, signIn, stateTab, test } from './support';

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

  // Review, California, under reduced motion: every step below asserts at once, never waiting for an animation.
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1440, height: 900 });
  await nav(page, 'Review').click();
  await expect(nav(page, 'Review')).toHaveAttribute('aria-current', 'page');
  await stateTab(page, 'CA').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^CA-/);
  const progress = page.getByTestId('progress');
  await expect(progress).toHaveText(/of 8 reviewed/);
  // The pill names the state; its mini ring's numbers are its description.
  await expect(stateTab(page, 'CA')).toHaveAccessibleDescription(/^\d of 8 reviewed$/);
  // "Sign California" appears only once every requirement is decided.
  await expect(page.getByRole('button', { name: 'Sign California' })).toHaveCount(0);
  await expectAccessible(page, 'Review at 1440 px');
  const note = page.getByLabel('Note', { exact: true });
  let gapNoteChecked = false;

  for (let done = 0; done < 8; done++) {
    await expect(progress).toHaveText(`${done} of 8 reviewed`);
    const finding = (await page.getByTestId('proposed-finding').textContent()) ?? '';
    const ref = (await page.getByTestId('requirement-heading').getAttribute('data-ref')) ?? '';
    if (finding.includes('Evidence missing')) {
      // Gap opens the note in place with the cursor in it; a gap without a note is refused inline.
      await page.getByRole('button', { name: 'Gap', exact: true }).click();
      await expect(note).toBeFocused();
      await page.getByRole('button', { name: 'Confirm gap' }).click();
      await expect(page.getByText('Confirming a gap requires a note')).toBeVisible();
      if (!gapNoteChecked) {
        await expectAccessible(page, 'Review with the gap note open');
        gapNoteChecked = true;
      }
      await note.fill('No telehealth content appears anywhere in the curriculum documents.');
      await page.getByRole('button', { name: 'Confirm gap' }).click();
    } else if (finding.includes('Ambiguous')) {
      await page.getByRole('button', { name: 'Add note' }).click();
      await note.fill('Only an elective covers this; please send the required-course coverage.');
      await page.getByRole('button', { name: 'Request evidence' }).click();
    } else {
      await page.getByRole('button', { name: 'Accept evidence' }).click();
    }
    // The decision has landed at once: announced, counted, the row's one status icon filled, the next one open.
    await expect(page.getByTestId('message')).toContainText(`${ref}:`);
    await expect(progress).toHaveText(`${done + 1} of 8 reviewed`);
    const row = page.getByRole('region', { name: 'Requirements' }).getByRole('button').filter({ hasText: new RegExp(`^${ref}(?!\\d)`) });
    await expect(row).toContainText(/, (accepted|evidence requested|gap confirmed)$/);
    if (done < 7) {
      await expect(page.getByTestId('requirement-heading')).not.toHaveAttribute('data-ref', ref);
    }
    // Reduced motion removes the motion: nothing is left running once the decision has landed.
    expect(await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length)).toBe(0);
  }
  await expect(progress).toHaveText('8 of 8 reviewed');
  await expect(stateTab(page, 'CA')).toHaveAccessibleDescription('8 of 8 reviewed');

  // Sign with the dialog's three outcome cards; Sign stays disabled until one is chosen.
  const signButton = page.getByRole('button', { name: 'Sign California' });
  await expect(signButton).toBeEnabled();
  const dialog = page.getByRole('dialog', { name: 'Sign California' });
  // At 400 px the signing action has a row of its own and the dialog stacks the cards under the seal preview.
  await page.setViewportSize({ width: 400, height: 860 });
  await signButton.click();
  await expect(dialog.getByRole('radio')).toHaveCount(3);
  await expectAccessible(page, 'Signing dialog at 400 px');
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toBeHidden();
  await page.setViewportSize({ width: 1440, height: 900 });
  await signButton.click();
  await expect(dialog.getByText('Determined to meet', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Determined not to meet', { exact: true })).toBeVisible();
  await expect(dialog.getByText('No determination made', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('radio')).toHaveCount(3);
  await expect(dialog.getByRole('button', { name: 'Sign', exact: true })).toBeDisabled();
  // Nothing is signed yet, so the seal preview says nothing to assistive technology.
  await expect(dialog.getByRole('img', { name: /^Signed by/ })).toHaveCount(0);
  await expectAccessible(page, 'Signing dialog');
  await dialog.getByText('Determined not to meet', { exact: true }).click();
  await expect(dialog.getByLabel('Determined not to meet')).toBeChecked();
  await expectAccessible(page, 'Signing dialog with an outcome chosen');
  await dialog.getByRole('button', { name: 'Sign', exact: true }).click();
  await expect(page.getByTestId('message')).toContainText('Determination for CA signed by Dana Whitfield');
  // The seal stamps in the dialog, named in one sentence; Done takes the focus.
  const signedDialog = page.getByRole('dialog', { name: 'Signed' });
  await expect(signedDialog.getByRole('img', { name: /^Signed by Dana Whitfield on .+: determined not to meet$/ })).toBeVisible();
  await expect(signedDialog.getByRole('button', { name: 'Done' })).toBeFocused();
  await expectAccessible(page, 'Signed seal');
  if (SCREENSHOTS) {
    await shoot(page, 'ui2-2-signed-1440');
  }
  await signedDialog.getByRole('button', { name: 'Done' }).click();
  await expect(signedDialog).toBeHidden();
  await expect(page.getByTestId('sign-bar')).toContainText('Determined not to meet');
  await expect(page.getByTestId('sign-bar').getByRole('img', { name: /^Signed by Dana Whitfield/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign California' })).toHaveCount(0);
  await expect(page.getByRole('group', { name: 'Decision' })).toHaveCount(0);
  await page.emulateMedia({ reducedMotion: 'no-preference' });

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
  // The state pills are listed alphabetically: Arizona, California, Colorado, Texas, Utah.
  await stateTab(page, 'AZ').focus();
  await page.keyboard.press('ArrowRight');
  await expect(stateTab(page, 'CA')).toHaveAttribute('aria-selected', 'true');
  // California is signed: the header carries its seal and there is no decision bar.
  await expect(page.getByTestId('sign-bar').getByRole('img', { name: /^Signed by Dana Whitfield/ })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Decision' })).toHaveCount(0);
  await page.keyboard.press('End');
  await expect(stateTab(page, 'UT')).toHaveAttribute('aria-selected', 'true');
  // Utah still has requirements to decide, so there is nothing to sign yet.
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^UT-/);
  await expect(page.getByRole('button', { name: /^Sign / })).toHaveCount(0);
});

test('keyboard shortcuts move through the list and record a decision', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  await stateTab(page, 'TX').click();
  const heading = page.getByTestId('requirement-heading');
  await expect(heading).toHaveAttribute('data-ref', /^TX-/);
  const progress = page.getByTestId('progress');
  await expect(progress).toHaveText(/^0 of \d+ reviewed$/);
  const first = await heading.getAttribute('data-ref');

  // Shortcuts act when focus is outside text fields: move focus to the list first.
  await page.locator('.lx-row[aria-current="true"]').focus();
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

  // A accepts the current requirement, announces it, and opens the next one that needs a decision. Motion is on here:
  // the landing plays, and keys pressed while the next requirement slides in act at once.
  await page.locator('.lx-row[aria-current="true"]').focus();
  await page.keyboard.press('a');
  await expect(page.getByTestId('message')).toContainText(`${first}: Accepted.`);
  await expect(progress).toHaveText(/^1 of \d+ reviewed$/);
  await expect(heading).toHaveAttribute('data-ref', second ?? '');
  await page.keyboard.press('k');
  await expect(heading).toHaveAttribute('data-ref', first ?? '');
  await page.keyboard.press('j');
  await expect(heading).toHaveAttribute('data-ref', second ?? '');
  await expect(page.locator('.p-toast')).toContainText(`Accepted: ${first}`);

  // G opens the note in place with the cursor in it, and letters typed there are text, not shortcuts.
  const note = page.getByLabel('Note', { exact: true });
  await expect(note).toHaveCount(0);
  await page.keyboard.press('g');
  await expect(note).toBeFocused();
  await expect(page.getByRole('button', { name: 'Confirm gap' })).toBeVisible();
  await page.keyboard.type('ja');
  await expect(note).toHaveValue('ja');
  await expect(progress).toHaveText(/^1 of \d+ reviewed$/);
  // Escape keeps a note with words in it; emptied, Escape closes it and focus returns to Gap.
  await page.keyboard.press('Escape');
  await expect(note).toBeFocused();
  await note.fill('');
  await page.keyboard.press('Escape');
  await expect(note).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Gap', exact: true })).toBeFocused();
});

test('at 400 px the review screen fits without horizontal page scroll', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  await nav(page, 'Review').click();
  await stateTab(page, 'UT').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^UT-/);
  await expect(page.getByRole('button', { name: 'Accept evidence' })).toBeVisible();
  // The decision bar stays at the foot of the window while the page scrolls under it.
  await page.locator('main').evaluate((main) => main.scrollTo(0, main.scrollHeight / 2));
  await expect(page.getByRole('group', { name: 'Decision' })).toBeInViewport();
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
