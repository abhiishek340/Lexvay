import { DANA, SCREENSHOTS, citedRequirement, expect, nav, openRequirement, shoot, signIn, test } from './support';

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
  await expect(page.getByRole('table', { name: 'Jurisdictions' })).toContainText('California');
  await shoot(page, 'program-overview');

  await nav(page, 'Review').click();
  await page.getByRole('tab', { name: 'TX', exact: true }).click();
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
  await nav(page, 'Evidence requests').click();
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
    const start = text.textContent!.indexOf('This');
    const range = document.createRange();
    range.setStart(text, start);
    range.setEnd(text, text.textContent!.indexOf('.', start) + 1);
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

  await nav(page, 'Changes').click();
  await expect(page.getByRole('table', { name: 'Change events' })).toBeVisible();
  await shoot(page, 'changes');

  await nav(page, 'Evidence report').click();
  await expect(page.getByRole('table', { name: 'Requirements and evidence' })).toBeVisible();
  await shoot(page, 'evidence-report');
  // After the review suite has signed California, its report carries the signed stamp.
  await page.getByRole('combobox', { name: 'Jurisdiction' }).click();
  await page.getByRole('option', { name: 'California (CA)' }).click();
  await expect(page.getByTestId('stamp')).toBeVisible();
  await shoot(page, 'evidence-report-ca');

  await nav(page, 'Audit trail').click();
  await expect(page.getByRole('table', { name: 'Audit trail' })).toBeVisible();
  await shoot(page, 'audit-trail');

  await nav(page, 'Determination register').click();
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
  await nav(page, 'Review').click();
  await page.getByRole('tab', { name: 'TX', exact: true }).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
  await shoot(page, 'review-400px');

  await openRequirement(page, 'UT', cited.ref);
  await page.getByRole('button', { name: 'Show on page' }).click();
  await expect(page.getByTestId('evidence-mark')).toBeFocused();
  await shoot(page, 'review-viewer-400px');
});
