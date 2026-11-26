import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

const PASSWORD = process.env['LEXVAY_DEMO_PASSWORD'] ?? 'lexvay-demo';

/** Fails on serious or critical accessibility issues on the current screen. */
async function expectAccessible(page: Page, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  const severe = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(severe, `${screen}: ${severe.map((v) => `${v.id} (${v.nodes.length} nodes)`).join(', ')}`).toEqual([]);
}

test('Dana Whitfield reviews every California requirement, signs, and sees the audit entries', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('dana.whitfield@northfield.example');
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Program overview with the permanent fictional-data banner.
  await expect(page.getByRole('heading', { name: 'M.S. Clinical Mental Health Counseling' })).toBeVisible();
  await expect(page.locator('.banner')).toContainText('Fictional data');
  await expect(page.locator('.counts')).toContainText('Jurisdictions');
  await expectAccessible(page, 'Program overview');

  // Review, California.
  await page.getByRole('link', { name: 'Review', exact: true }).click();
  await page.getByRole('tab', { name: 'CA' }).click();
  await expect(page.locator('.progress')).toHaveText(/of 8 reviewed/);
  await expectAccessible(page, 'Review');

  for (let done = 0; done < 8; done++) {
    await expect(page.locator('.progress')).toHaveText(`${done} of 8 reviewed`);
    const finding = (await page.locator('.detail .finding.big').textContent()) ?? '';
    const ref = ((await page.locator('.detail h2').textContent()) ?? '').split('·')[0].trim();
    if (finding.includes('Evidence missing')) {
      await page.getByLabel('Note').fill('No telehealth content appears anywhere in the curriculum documents.');
      await page.getByRole('button', { name: 'Confirm gap' }).click();
    } else if (finding.includes('Ambiguous')) {
      await page.getByLabel('Note').fill('Only an elective covers this; please send the required-course coverage.');
      await page.getByRole('button', { name: 'Request evidence from program director' }).click();
    } else {
      await page.getByRole('button', { name: 'Accept evidence' }).click();
    }
    await expect(page.getByTestId('message')).toContainText(ref);
  }
  await expect(page.locator('.progress')).toHaveText('8 of 8 reviewed');

  // Sign with the dialog's three outcomes.
  const signButton = page.getByRole('button', { name: 'Sign determination' });
  await expect(signButton).toBeEnabled();
  await signButton.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Determined to meet')).toBeVisible();
  await expect(dialog.getByText('Determined not to meet')).toBeVisible();
  await expect(dialog.getByText('No determination made')).toBeVisible();
  await dialog.getByLabel('Determined not to meet').check();
  await dialog.getByRole('button', { name: 'Sign', exact: true }).click();
  await expect(page.getByTestId('message')).toContainText('Determination for CA signed by Dana Whitfield');
  await expect(page.locator('.sign')).toContainText('Determined not to meet');

  // Other screens render and pass the accessibility check.
  await page.getByRole('link', { name: 'Changes', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Changes' })).toBeVisible();
  await expectAccessible(page, 'Changes');

  await page.getByRole('link', { name: 'Evidence report', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Evidence report' })).toBeVisible();
  await expect(page.locator('.stamp')).toContainText(/Draft\. Not a determination|Signed determination/);
  await expectAccessible(page, 'Evidence report');

  // Audit trail shows the review decisions and the signature.
  await page.getByRole('link', { name: 'Audit trail', exact: true }).click();
  const audit = page.getByRole('table', { name: 'Audit trail' });
  await expect(audit).toContainText('Determination signed');
  await expect(audit).toContainText('Review decision recorded');
  await expect(audit).toContainText('outcome: DOES_NOT_MEET');
  await expectAccessible(page, 'Audit trail');

  // The screens never use the forbidden phrases.
  const text = (await page.locator('body').innerText()).toLowerCase();
  for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
    expect(text).not.toContain(forbidden);
  }
});

test('the review tabs are keyboard operable and the login page is accessible', async ({ page }) => {
  await page.goto('/login');
  await expectAccessible(page, 'Login');
  await page.getByLabel('Email').fill('priya.raman@northfield.example');
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Review', exact: true }).click();
  // Tabs are listed alphabetically: AZ, CA, CO, TX, UT.
  await page.getByRole('tab', { name: 'AZ' }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'CA' })).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('End');
  await expect(page.getByRole('tab', { name: 'UT' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('button', { name: 'Sign determination' })).toBeDisabled();
  await expect(page.getByText('Only a signer can sign.')).toBeVisible();
});
