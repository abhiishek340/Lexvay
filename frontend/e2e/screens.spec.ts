import { DANA, SCREENSHOTS, expect, nav, shoot, signIn, test } from './support';

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

  await page.getByRole('button', { name: 'Keyboard shortcuts' }).click();
  await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toBeVisible();
  await shoot(page, 'review-shortcuts');
  await page.keyboard.press('Escape');

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

  await page.setViewportSize({ width: 400, height: 860 });
  await nav(page, 'Review').click();
  await page.getByRole('tab', { name: 'TX', exact: true }).click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^TX-/);
  await shoot(page, 'review-400px');
});
