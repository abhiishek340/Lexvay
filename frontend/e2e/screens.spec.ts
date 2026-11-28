import { readFileSync } from 'node:fs';
import { ADMIN, DANA, JORDAN, SCREENSHOTS, apiPost, changeRows, citedRequirement, expect, goTo, nav, openRequirement, programIdFromUrl, shoot, signIn, stateTab, switchUser, test } from './support';

/**
 * Screenshots of every screen in both themes for docs/screens. Not part of the regular suite:
 * run with LEXVAY_SCREENSHOTS=1 (for example `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=1 make e2e`).
 */
test.skip(!SCREENSHOTS, 'screenshots are taken only on request');

test('screenshots of every screen in light and dark', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await shoot(page, 'sign-in');

  await signIn(page, DANA);
  await expect(page.getByTestId('state-row-CA')).toContainText('California');
  await shoot(page, 'program-overview');

  await goTo(page, 'Review');
  await stateTab(page, 'TX').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
  await shoot(page, 'review');

  // The document viewer: the cited page with the quote marked, on a requirement whose evidence is a syllabus.
  const cited = await citedRequirement(page, 'UT');
  await openRequirement(page, 'UT', cited.ref);
  await expect(page.getByTestId('evidence-mark')).toBeInViewport();
  await shoot(page, 'review-viewer');

  await page.getByRole('button', { name: 'Keyboard shortcuts' }).click();
  await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toBeVisible();
  await shoot(page, 'review-shortcuts');
  await page.keyboard.press('Escape');

  // Evidence requests: the evidence-requests suite leaves one open request on Utah and one answered and accepted.
  await goTo(page, 'Evidence requests');
  const open = page.getByRole('table', { name: 'Evidence requests' }).getByRole('button', { name: /^Provide evidence for / }).first();
  await expect(open).toBeVisible();
  await shoot(page, 'evidence-requests');
  await open.click();
  const drawer = page.getByRole('dialog', { name: 'Provide evidence' });
  await drawer.getByRole('combobox', { name: 'Program document' }).click();
  await page.getByRole('option', { name: /^coun605-syllabus\.pdf syllabus$/ }).click();
  await expect(drawer.locator('lx-document-viewer pre')).toBeVisible();
  await drawer.locator('lx-document-viewer pre').evaluate((pre) => {
    const text = Array.from(pre.childNodes).find((n) => n.nodeType === Node.TEXT_NODE)!;
    const sentence = /[A-Z][^.\n]{30,}\./.exec(text.textContent!)!;
    const range = document.createRange();
    range.setStart(text, sentence.index);
    range.setEnd(text, sentence.index + sentence[0].length);
    document.getSelection()!.removeAllRanges();
    document.getSelection()!.addRange(range);
    pre.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await expect(drawer.getByTestId('selected-passage').locator('blockquote')).toBeVisible();
  await shoot(page, 'evidence-request-drawer');
  await page.keyboard.press('Escape');
  await expect(drawer).toBeHidden();

  const overview = await (await page.request.get(`/api/v1/programs/${cited.programId}/overview`)).json();
  const ut = await (await page.request.get(`/api/v1/assessments/${overview.assessment.id}/mappings?jurisdiction=UT`)).json();
  const answered = (ut.mappings as { responseCount: number; requirement: { ref: string } }[]).find((m) => m.responseCount > 0)!;
  await openRequirement(page, 'UT', answered.requirement.ref);
  await expect(page.getByTestId('provided-evidence').getByTestId('evidence-mark')).toBeVisible();
  await shoot(page, 'review-provided-evidence');

  await goTo(page, 'Changes');
  await expect(changeRows(page).first()).toBeVisible();
  await shoot(page, 'changes');

  await goTo(page, 'Evidence report');
  await expect(page.getByRole('list', { name: /requirements?$/ })).toBeVisible();
  await shoot(page, 'evidence-report');
  // After the review suite has signed California, its report carries the seal.
  await stateTab(page, 'CA').click();
  await expect(page.getByRole('img', { name: /^Signed by / })).toBeVisible();
  await shoot(page, 'evidence-report-ca');

  await goTo(page, 'Audit trail');
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible();
  await shoot(page, 'audit-trail');

  await goTo(page, 'Determination register');
  await expect(page.getByRole('table', { name: 'Determination register' }).locator('tbody tr')).toHaveCount(1);
  await shoot(page, 'register');
  await page.getByRole('tab', { name: 'By program' }).click();
  await expect(page.getByRole('region', { name: 'No determination made', exact: true })).toBeVisible();
  await shoot(page, 'register-by-program');
  // On paper the theme makes no difference: both captures are black on white.
  await page.emulateMedia({ media: 'print' });
  await shoot(page, 'register-print');
  await page.emulateMedia({ media: 'screen' });

  await page.setViewportSize({ width: 400, height: 860 });
  await goTo(page, 'Review');
  await stateTab(page, 'TX').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
  await shoot(page, 'review-400px');

  await openRequirement(page, 'UT', cited.ref);
  await page.getByRole('button', { name: 'Show on page' }).click();
  await expect(page.getByTestId('evidence-mark')).toBeFocused();
  await shoot(page, 'review-viewer-400px');
});

/**
 * UI3-0: sign-in, the overview, the shell (the rail expanded and collapsed, the 400 px tab bar and its More menu, the
 * command palette, the work queue and the account menu) and the shared components, at 1440 and 400 px.
 */
test('UI3-0: sign-in, the overview and the shell in design v3 at 1440 and 400 px', async ({ page }) => {
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await page.goto('/login');
    await expect(page.getByRole('group', { name: 'Demo accounts' })).toBeVisible();
    await shoot(page, `ui3-0-sign-in-${width}`);

    await signIn(page, DANA);
    await expect(page.getByTestId('state-row-CA')).toContainText('Signed by Dana Whitfield');
    await shoot(page, `ui3-0-overview-${width}`);
    await page.getByRole('button', { name: 'Help' }).click();
    await expect(page.getByRole('dialog', { name: 'Help' })).toBeVisible();
    await shoot(page, `ui3-0-overview-help-${width}`);
    await page.keyboard.press('Escape');

    if (width === 1440) {
      await page.getByRole('button', { name: 'Collapse navigation' }).click();
      await shoot(page, 'ui3-0-shell-collapsed-1440');
      await page.getByRole('button', { name: 'Expand navigation' }).click();

      await page.keyboard.press('Control+k');
      const palette = page.getByRole('dialog', { name: 'Search or jump to' });
      await palette.getByRole('combobox').fill('rev');
      await expect(palette.getByRole('menuitem').first()).toBeVisible();
      await shoot(page, 'ui3-0-command-palette-1440');
      await page.keyboard.press('Escape');
      await expect(palette).toBeHidden();

      await page.getByRole('button', { name: /^Work queue/ }).click();
      await expect(page.getByRole('dialog', { name: 'Work queue' }).getByRole('link').first()).toBeVisible();
      await shoot(page, 'ui3-0-work-queue-1440');
      await page.keyboard.press('Escape');

      await page.getByRole('button', { name: /^Account, / }).click();
      await expect(page.getByRole('group', { name: 'Theme' })).toBeVisible();
      await shoot(page, 'ui3-0-account-menu-1440');
      await page.keyboard.press('Escape');

      // The shared components (development harness, dev/ui).
      await page.goto('/dev/ui');
      await expect(page.getByRole('heading', { name: 'Status marks' })).toBeVisible();
      await shoot(page, 'ui3-0-components-1440');
    } else {
      await page.getByRole('navigation', { name: 'Screens' }).getByRole('button', { name: /^More screens/ }).click();
      await expect(page.getByRole('menu', { name: 'More screens' })).toBeVisible();
      await shoot(page, 'ui3-0-tab-bar-more-400');
      await page.keyboard.press('Escape');
    }
    await page.context().clearCookies();
  }
});

/** UI3-0: every other screen as it looks once its callers moved to the v3 components, the start of UI3-1 to UI3-3. */
test('UI3-0: the starting point of every other screen', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, DANA);
  await nav(page, 'Review').click();
  await stateTab(page, 'TX').click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
  await shoot(page, 'ui3-0-before-review');

  await nav(page, 'Coverage').click();
  await expect(page.getByRole('table', { name: /^Coverage, / })).toBeVisible();
  await shoot(page, 'ui3-0-before-coverage');

  await nav(page, 'Evidence requests').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Evidence requests' })).toBeVisible();
  await shoot(page, 'ui3-0-before-requests');

  await nav(page, 'Changes').click();
  await expect(changeRows(page).first()).toBeVisible();
  await shoot(page, 'ui3-0-before-changes');

  await nav(page, 'Rules').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible();
  await shoot(page, 'ui3-0-before-rules');

  await nav(page, 'Program setup').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Program setup' })).toBeVisible();
  await shoot(page, 'ui3-0-before-program-setup');

  await nav(page, 'Evidence report').click();
  await stateTab(page, 'CA').click();
  await expect(page.getByRole('img', { name: /^Signed by / })).toBeVisible();
  await shoot(page, 'ui3-0-before-report');

  await nav(page, 'Audit trail').click();
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible();
  await shoot(page, 'ui3-0-before-audit');

  await nav(page, 'Determination register').click();
  await expect(page.getByRole('table', { name: 'Determination register' }).locator('tbody tr')).toHaveCount(1);
  await shoot(page, 'ui3-0-before-register');

  await switchUser(page, ADMIN);
  await nav(page, 'People').click();
  await expect(page.getByRole('table', { name: 'People' })).toBeVisible();
  await shoot(page, 'ui3-0-before-people');

  await page.goto('/setup');
  await expect(page.getByRole('heading', { level: 1, name: 'Choose a password' })).toBeVisible();
  await shoot(page, 'ui3-0-before-setup');
});

/**
 * UI2-2: Review and signing at 1440 and 400 px. Arizona's requirements are decided through the API first, as a reviewer
 * would decide them, so that its signing dialog can be shown with an outcome chosen; the dialog is cancelled, so the
 * register still holds California alone.
 */
test('UI2-2: Review, a decided requirement, the gap note and the signing dialog at 1440 and 400 px', async ({ page }) => {
  await signIn(page, DANA);
  const programId = await programIdFromUrl(page);
  const overview = await (await page.request.get(`/api/v1/programs/${programId}/overview`)).json();
  const az = await (await page.request.get(`/api/v1/assessments/${overview.assessment.id}/mappings?jurisdiction=AZ`)).json();
  const decisions = { EVIDENCE_FOUND: 'ACCEPTED', AMBIGUOUS: 'EVIDENCE_REQUESTED', EVIDENCE_MISSING: 'GAP_CONFIRMED' } as const;
  for (const m of az.mappings as { id: string; finding: keyof typeof decisions; decision: unknown; stale: boolean }[]) {
    if (m.decision === null && !m.stale) {
      const kind = decisions[m.finding];
      const note = kind === 'ACCEPTED' ? null : 'Recorded for the screenshot review.';
      expect((await apiPost(page, `/api/v1/mappings/${m.id}/reviews`, { kind, note })).status()).toBe(201);
    }
  }

  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await goTo(page, 'Review');
    await stateTab(page, 'TX').click();
    await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
    await page.waitForTimeout(900);
    await shoot(page, `ui2-2-review-${width}`);

    // A decision lands: the row's icon fills and the next requirement slides in.
    const decided = await page.getByTestId('requirement-heading').getAttribute('data-ref');
    await page.getByRole('button', { name: 'Accept evidence' }).click();
    await expect(page.getByTestId('message')).toContainText(`${decided}: Accepted.`);
    await page.waitForTimeout(900);
    await shoot(page, `ui2-2-decided-${width}`);

    await page.getByRole('button', { name: 'Gap', exact: true }).click();
    await expect(page.getByLabel('Note', { exact: true })).toBeFocused();
    await page.keyboard.type('No course covers the state law requirement.');
    await page.waitForTimeout(400);
    await shoot(page, `ui2-2-gap-note-${width}`);

    await stateTab(page, 'AZ').click();
    const sign = page.getByRole('button', { name: 'Sign Arizona' });
    await expect(sign).toBeVisible();
    await page.waitForTimeout(900);
    await shoot(page, `ui2-2-ready-to-sign-${width}`);
    await sign.click();
    const dialog = page.getByRole('dialog', { name: 'Sign Arizona' });
    await dialog.getByText('Determined to meet', { exact: true }).click();
    await page.waitForTimeout(400);
    await shoot(page, `ui2-2-signing-${width}`);
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();
  }
});

/**
 * UI3-2: Changes, the audit trail, the evidence report (draft, signed, the package being prepared, the check dialog with
 * a match), the register (both tabs), People and the setup page, at 1440 and 400 px. California is signed by the
 * review suite. Package status answers are held back while the "Preparing package…" capture is taken.
 */
test('UI3-2: changes, audit trail, report, register, people and setup in design v3 at 1440 and 400 px', async ({ page }) => {
  await signIn(page, ADMIN);
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await goTo(page, 'Changes');
    await changeRows(page).first().getByRole('button', { name: / changed from / }).click();
    await shoot(page, `ui3-2-changes-${width}`);

    await goTo(page, 'Audit trail');
    const entries = page.getByRole('main').getByRole('listitem');
    await expect(entries.nth(2)).toBeVisible();
    await entries.nth(1).getByRole('button').click();
    await page.locator('main').evaluate((main) => main.scrollTo(0, 0));
    await shoot(page, `ui3-2-audit-${width}`);

    await goTo(page, 'Evidence report');
    await expect(page.getByRole('note', { name: 'Draft. Not a determination.' })).toBeVisible();
    await shoot(page, `ui3-2-report-draft-${width}`);
    await stateTab(page, 'CA').click();
    await expect(page.getByRole('img', { name: /^Signed by / })).toBeVisible();
    await expect(page.getByRole('list', { name: '8 requirements' })).toBeVisible();
    await shoot(page, `ui3-2-report-signed-${width}`);

    const pdf = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download PDF' }).click();
    const report = { name: 'report.pdf', mimeType: 'application/pdf', buffer: readFileSync(await (await pdf).path()) };
    let release = (): void => undefined;
    const held = new Promise<void>((resolve) => { release = resolve; });
    await page.route('**/api/v1/packages/*', async (route) => { await held; await route.continue(); });
    await page.getByRole('button', { name: 'Download evidence package' }).click();
    await expect(page.getByRole('button', { name: 'Preparing package…' })).toBeDisabled();
    await shoot(page, `ui3-2-report-package-${width}`);
    const zip = page.waitForEvent('download');
    release();
    await zip;
    await page.unroute('**/api/v1/packages/*');

    await page.getByRole('button', { name: 'Check a file' }).click();
    const check = page.getByRole('dialog', { name: 'Check a file' });
    await expect(check.getByText('Drop a PDF here')).toBeVisible();
    await shoot(page, `ui3-2-check-${width}`);
    await check.locator('input[type=file]').setInputFiles(report);
    await expect(check.getByTestId('check-result')).toHaveAttribute('data-result', 'signed');
    await shoot(page, `ui3-2-check-match-${width}`);
    await page.keyboard.press('Escape');

    await goTo(page, 'Determination register');
    await expect(page.getByRole('table', { name: 'Determination register' }).locator('tbody tr')).toHaveCount(1);
    await shoot(page, `ui3-2-register-${width}`);
    await page.getByRole('tab', { name: 'By program' }).click();
    await expect(page.getByRole('region', { name: 'No determination made', exact: true })).toBeVisible();
    await shoot(page, `ui3-2-register-by-program-${width}`);

    await goTo(page, 'People');
    await expect(page.getByRole('table', { name: 'People' }).locator('tbody tr').first()).toBeVisible();
    await shoot(page, `ui3-2-people-${width}`);
  }

  await page.context().clearCookies();
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await page.goto('/setup#code=screenshot');
    await page.getByLabel('New password', { exact: true }).fill('Granite river lantern');
    await shoot(page, `ui3-2-setup-${width}`);
  }
});

/** UI2-4: the evidence request inbox and the answer drawer with a selected passage, at 1440 and 400 px. */
test('UI2-4: the evidence request inbox and the answer drawer at 1440 and 400 px', async ({ page }) => {
  await signIn(page, JORDAN);
  for (const [width, height] of [[1440, 900], [400, 860]] as const) {
    await page.setViewportSize({ width, height });
    await goTo(page, 'Evidence requests');
    await page.locator('p-selectbutton').getByText('All').click();
    const open = page.getByRole('table', { name: 'Evidence requests' }).getByRole('button', { name: /^Provide evidence for / }).first();
    await expect(open).toBeVisible();
    await shoot(page, `ui2-4-requests-inbox-${width}`);
    await open.click();
    const drawer = page.getByRole('dialog', { name: 'Provide evidence' });
    await drawer.getByRole('combobox', { name: 'Program document' }).click();
    await page.getByRole('option', { name: /^coun605-syllabus\.pdf syllabus$/ }).click();
    await expect(drawer.locator('lx-document-viewer pre')).toBeVisible();
    await drawer.locator('lx-document-viewer pre').evaluate((pre) => {
      const text = Array.from(pre.childNodes).find((n) => n.nodeType === Node.TEXT_NODE)!;
      const sentence = /[A-Z][^.\n]{30,}\./.exec(text.textContent!)!;
      const range = document.createRange();
      range.setStart(text, sentence.index);
      range.setEnd(text, sentence.index + sentence[0].length);
      document.getSelection()!.removeAllRanges();
      document.getSelection()!.addRange(range);
      pre.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await expect(drawer.getByTestId('selected-passage').locator('blockquote')).toBeVisible();
    await shoot(page, `ui2-4-requests-drawer-${width}`);
    await page.keyboard.press('Escape');
    await expect(drawer).toBeHidden();
  }
});
