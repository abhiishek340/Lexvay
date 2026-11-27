import { Page } from '@playwright/test';
import { JORDAN, PRIYA, SCREENSHOTS, expect, expectAccessible, nav, shoot, signIn, test } from './support';

/*
 * The rule workspace against the seeded Northfield data. The tests share one database and run in order:
 * a contributor enters a Nevada version (not one of the program's jurisdictions, so no assessment or change
 * event is touched), and a reviewer then corrects and approves its list.
 */

const REVISION = /Colorado \(fictional\) 2026 revision 2/;
const NEVADA_LABEL = 'Demo rule set - Nevada (fictional) 2027';

function versions(page: Page) {
  return page.getByRole('listbox', { name: 'Rule versions' });
}

/** docs/screens are taken at the size the other screens use, then the test continues at its own size. */
/** The edit grid never scrolls sideways at a window of 1024 px or wider. */
async function expectNoSidewaysScroll(page: Page, widths: number[]): Promise<void> {
  const size = page.viewportSize();
  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 });
    const overflow = await page.getByRole('table', { name: 'Requirements, editable' }).evaluate((table) => {
      const container = table.parentElement!;
      return Math.max(container.scrollWidth - container.clientWidth, table.scrollWidth - container.clientWidth);
    });
    expect(overflow, `edit grid at ${width} px`).toBeLessThanOrEqual(0);
  }
  if (size) { await page.setViewportSize(size); }
}

async function capture(page: Page, name: string): Promise<void> {
  if (!SCREENSHOTS) { return; }
  const size = page.viewportSize();
  await page.setViewportSize({ width: 1440, height: 900 });
  await shoot(page, name);
  if (size) { await page.setViewportSize(size); }
}

test('a reviewer reads the seeded Colorado revision: its list, its rule text and what it changed', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Rules').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Rules' })).toBeVisible();
  await expect(nav(page, 'Rules')).toHaveAttribute('aria-current', 'page');

  // The seed approves both Colorado versions; the revision supersedes the original.
  const revision = versions(page).getByRole('option', { name: REVISION });
  await expect(revision).toContainText('Approved');
  await expect(versions(page).getByRole('option', { name: /Colorado \(fictional\) 2026, effective/ })).toContainText('Superseded');
  await expectAccessible(page, 'Rules list');
  await capture(page, 'rules-list');

  await revision.click();
  await expect(page).toHaveURL(/\/rules\/[0-9a-f-]{36}$/);
  await expect(revision).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { level: 2, name: 'Demo rule set - Colorado (fictional) 2026 revision 2' })).toBeVisible();
  await expect(page.getByTestId('version-state')).toContainText(/Approved by Dr\. Priya Raman on \d{4}-\d{2}-\d{2}/);

  // An approved list has no edit controls at all.
  const requirements = page.getByRole('table', { name: 'Requirements' });
  await expect(requirements.locator('tbody tr')).toHaveCount(9);
  await expect(requirements.locator('tbody tr', { hasText: 'CO-5' })).toContainText('Internship hours >= 700');
  await expect(page.getByRole('button', { name: 'Edit list' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Approve requirements' })).toHaveCount(0);
  await expectAccessible(page, 'Rule version requirements');

  await page.getByRole('tab', { name: 'Rule text' }).click();
  await expect(page.getByTestId('rule-text')).toContainText('Rule 5. Internship. The applicant shall have completed at least 700 clock hours');
  await expect(page.getByTestId('page-position')).toHaveText(/Page\s+1\s+of\s+1/);
  await expectAccessible(page, 'Rule text');

  // Compare defaults to the superseded version: exactly one changed requirement, CO-5, number and text.
  await page.getByRole('tab', { name: 'Compare' }).click();
  await expect(page.getByTestId('compare-summary')).toHaveText(/1 changed\s*·\s*0 added\s*·\s*0 removed\s*·\s*8 unchanged/);
  const changes = page.getByRole('table', { name: 'Requirement changes' });
  await expect(changes.locator('tbody tr')).toHaveCount(9);
  const changed = changes.locator('tr[data-status="CHANGED"]');
  await expect(changed).toHaveCount(1);
  await expect(changed).toContainText('CO-5');
  await expect(changed.getByTestId('numeric-change').locator('del')).toHaveText('Internship hours >= 600');
  await expect(changed.getByTestId('numeric-change').locator('ins')).toHaveText('Internship hours >= 700');
  await expect(changed.getByTestId('text-diff').locator('del')).toHaveText('600');
  await expect(changed.getByTestId('text-diff').locator('ins')).toHaveText('700');
  await capture(page, 'rules-compare');
  await page.getByRole('button', { name: 'Rule text' }).click();
  const ruleTextDiff = page.getByTestId('rule-text-diff');
  await expect(ruleTextDiff.locator('ins', { hasText: '700' })).toHaveCount(1);
  await expect(ruleTextDiff.locator('del', { hasText: '600' })).toHaveCount(1);
  await expectAccessible(page, 'Compare');

  // The list is a listbox: arrow keys move, Enter opens. The original version supersedes nothing.
  await versions(page).focus();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  const original = versions(page).getByRole('option', { name: /Colorado \(fictional\) 2026, effective/ });
  await expect(original).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByTestId('compare-empty')).toHaveText('This is the first version for Colorado; choose a version to compare with.');
  await expect(page.getByRole('heading', { level: 2, name: 'Demo rule set - Colorado (fictional) 2026' })).toBeVisible();

  const text = (await page.locator('body').innerText()).toLowerCase();
  for (const forbidden of [['compl', 'iant'].join(''), ['meets requ', 'irements'].join('')]) {
    expect(text).not.toContain(forbidden);
  }
});

test('a contributor enters a new rule version and watches its extraction', async ({ page }) => {
  await signIn(page, JORDAN);
  await nav(page, 'Rules').click();
  const open = page.getByRole('button', { name: 'New rule version' });
  await open.click();
  const dialog = page.getByRole('dialog', { name: 'New rule version' });
  await expect(dialog.getByLabel('License type')).toHaveValue('CLINICAL_MENTAL_HEALTH_COUNSELING');

  // Missing fields are named inline before anything is sent.
  await dialog.getByRole('button', { name: 'Enter rule version' }).click();
  await expect(dialog.getByText('Choose a jurisdiction.')).toBeVisible();
  await expect(dialog.getByText('Paste the rule text.')).toBeVisible();
  await expectAccessible(page, 'New rule version with problems');

  await dialog.getByRole('combobox', { name: 'Jurisdiction' }).click();
  await page.getByRole('searchbox').fill('Nevada');
  await page.getByRole('option', { name: 'Nevada (NV)' }).click();
  await dialog.getByLabel('Label').fill(NEVADA_LABEL);
  await dialog.getByLabel('Source URL').fill('https://example.invalid/demo/nevada');
  // The date picker reads typed keys (it ignores a value set without them).
  await dialog.getByLabel('Effective date').pressSequentially('2027-01-01');
  await dialog.getByLabel('Effective date').press('Escape');
  await dialog.getByLabel('Rule text').fill('Demo rule set - Nevada (fictional) 2027. Fictional. Prototype data. Not the law of any state.\n'
    + 'Rule 1. Degree. An applicant shall hold a graduate counseling degree of at least 60 semester credit hours.\n'
    + 'Rule 2. Ethics. The degree shall include coursework in professional ethics and legal issues in counseling.\n');
  await expect(dialog.getByText(/^\d[\d ]* of 500 000 characters$/)).toBeVisible();
  await expect(dialog.getByRole('combobox', { name: 'Supersedes (optional)' })).toBeDisabled();
  await expectAccessible(page, 'New rule version dialog');
  await capture(page, 'rules-new-version');

  await dialog.getByRole('button', { name: 'Enter rule version' }).click();
  await expect(dialog).toBeHidden();
  await expect(open).toBeFocused();
  await expect(page.getByTestId('message')).toContainText('Extraction started');
  await expect(page.locator('.p-toast')).toContainText('Extraction started');

  // The new version is selected and extracting; the list polls until the job ends.
  const nevada = versions(page).getByRole('option', { name: /Nevada \(fictional\) 2027/ });
  await expect(nevada).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByTestId('version-state')).toContainText('Extracting');
  await expect(page.getByTestId('version-state')).toContainText('Not approved');
  await expectAccessible(page, 'Rule version extracting');

  // The e2e backend runs FakeLlmClient with no script for this label, so extraction fails after its retries.
  await expect(page.getByTestId('version-state')).toContainText('Extraction failed', { timeout: 30_000 });
  // In words, with the internal error behind "Technical detail"; the list row shows only the status.
  const failed = page.getByTestId('extraction-failed');
  await expect(failed).toContainText('Requirement extraction did not produce a list. A reviewer can enter the list by hand with Edit list.');
  await expect(failed.getByText(/FakeLlmClient/)).toBeHidden();
  await failed.getByText('Technical detail').click();
  await expect(failed.getByText(/FakeLlmClient has no scripted response/)).toBeVisible();
  await expect(nevada).not.toContainText('FakeLlmClient');
  // Correcting and approving belong to reviewers.
  await expect(page.getByRole('button', { name: 'Edit list' })).toHaveCount(0);
  await expectAccessible(page, 'Rule version extraction failed');
});

test('a reviewer enters the list by hand, corrects it, saves, reloads and approves it', async ({ page }) => {
  await signIn(page, PRIYA);
  await nav(page, 'Rules').click();
  await versions(page).getByRole('option', { name: /Nevada \(fictional\) 2027/ }).click();
  await expect(page.getByTestId('version-state')).toContainText('Extraction failed');

  await page.getByRole('button', { name: 'Edit list' }).click();
  const grid = page.getByRole('table', { name: 'Requirements, editable' });
  await page.getByRole('button', { name: 'Add requirement' }).click();
  await expect(page.getByRole('textbox', { name: 'Row 1 ref' })).toBeFocused();
  await page.keyboard.type('NV-1');
  await grid.getByRole('textbox', { name: 'NV-1 title' }).fill('Credit hours');
  await grid.getByRole('combobox', { name: 'NV-1 type' }).click();
  await page.getByRole('option', { name: 'Numeric', exact: true }).click();
  await grid.getByRole('combobox', { name: 'NV-1 numeric field' }).click();
  await page.getByRole('option', { name: 'Total credits' }).click();
  await grid.getByRole('combobox', { name: 'NV-1 operator' }).click();
  await page.getByRole('option', { name: '>=', exact: true }).click();
  await grid.getByRole('spinbutton', { name: 'NV-1 value' }).fill('60');
  await grid.getByRole('textbox', { name: 'NV-1 text' }).fill('a graduate counseling degree of at least 60 semester credit hours');

  await page.getByRole('button', { name: 'Add requirement' }).click();
  await expect(page.getByRole('textbox', { name: 'Row 2 ref' })).toBeFocused();
  await page.keyboard.type('NV-2');
  await grid.getByRole('combobox', { name: 'NV-2 type' }).click();
  await page.getByRole('option', { name: 'Subject', exact: true }).click();
  await grid.getByRole('textbox', { name: 'NV-2 text' }).fill('coursework in professional ethics and legal issues in counseling');
  await expectAccessible(page, 'Edit grid');
  await expectNoSidewaysScroll(page, [1024, 1280, 1440]);

  // The server rejects a blank title; its message appears under that row.
  await page.getByRole('button', { name: 'Save list' }).click();
  await expect(grid.getByTestId('row-problems')).toHaveText('NV-2: Title must not be blank.');
  await expect(grid.getByRole('textbox', { name: 'NV-2 title' })).toHaveAttribute('aria-describedby', /problems-/);
  await expectAccessible(page, 'Edit grid with a row problem');

  await grid.getByRole('textbox', { name: 'NV-2 title' }).fill('Ethics coursework');
  await capture(page, 'rules-edit');
  await page.getByRole('button', { name: 'Save list' }).click();
  await expect(page.getByTestId('message')).toContainText('Requirement list saved');
  await expect(page.getByTestId('version-state')).toContainText('Needs approval');
  await expect(page.getByText('1 rule version waiting for approval')).toBeAttached();

  // Edit one title; choosing another version first asks, and Keep editing keeps the edit.
  await page.getByRole('button', { name: 'Edit list' }).click();
  await grid.getByRole('textbox', { name: 'NV-2 title' }).fill('Ethics and law coursework');
  const nevadaUrl = page.url();
  await versions(page).getByRole('option', { name: REVISION }).click();
  const discard = page.getByRole('alertdialog', { name: 'Discard unsaved changes to this list?' });
  await expect(discard).toBeVisible();
  await expect(discard.getByRole('button', { name: 'Keep editing', exact: true })).toBeFocused();
  await expectAccessible(page, 'Unsaved changes dialog');
  await discard.getByRole('button', { name: 'Keep editing', exact: true }).click();
  await expect(discard).toBeHidden();
  expect(page.url()).toBe(nevadaUrl);
  await expect(versions(page).getByRole('option', { name: /Nevada \(fictional\) 2027/ })).toHaveAttribute('aria-selected', 'true');
  await expect(grid.getByRole('textbox', { name: 'NV-2 title' })).toHaveValue('Ethics and law coursework');

  // Save, reload: the server kept it.
  await page.getByRole('button', { name: 'Save list' }).click();
  await expect(page.getByTestId('message')).toContainText('Requirement list saved');
  await page.reload();
  const requirements = page.getByRole('table', { name: 'Requirements' });
  await expect(requirements.locator('tbody tr', { hasText: 'NV-2' })).toContainText('Ethics and law coursework');
  await expect(requirements.locator('tbody tr', { hasText: 'NV-1' })).toContainText('Total credits >= 60');

  // The approval dialog says what approving means; cancelling returns focus to the button.
  const approve = page.getByRole('button', { name: 'Approve requirements' });
  await approve.click();
  const dialog = page.getByRole('dialog', { name: 'Approve requirements' });
  await expect(dialog).toContainText('Approving locks this list. Assessments for Nevada will use these 2 requirements. The list can no longer be edited.');
  await expectAccessible(page, 'Approve dialog');
  await capture(page, 'rules-approve');
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(approve).toBeFocused();

  await approve.click();
  await dialog.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByTestId('message')).toContainText('Requirements approved');
  await expect(page.locator('.p-toast')).toContainText('Requirements approved');
  await expect(page.getByTestId('version-state')).toContainText(/Approved by Dr\. Priya Raman on \d{4}-\d{2}-\d{2}/);
  await expect(page.getByRole('button', { name: 'Edit list' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Approve requirements' })).toHaveCount(0);
  await expect(page.locator('#version-heading')).toBeFocused();
  await expect(page.getByText('rule version waiting for approval')).toHaveCount(0);
  await expectAccessible(page, 'Approved version');

  await nav(page, 'Audit trail').click();
  const audit = page.getByRole('table', { name: 'Audit trail' });
  await expect(audit).toContainText('Requirement list approved');
  await expect(audit).toContainText('Requirement list replaced');
  await expect(audit.locator('tbody tr', { hasText: 'Requirement list approved' }).first()).toContainText('Dr. Priya Raman');
});

test('at 400 px the rule workspace stacks without horizontal page scroll', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  await nav(page, 'Rules').click();
  await versions(page).getByRole('option', { name: REVISION }).click();
  await expect(page.getByRole('table', { name: 'Requirements' })).toBeVisible();
  const overflow = await page.evaluate(() => {
    const main = document.querySelector('main')!;
    return { page: document.documentElement.scrollWidth - window.innerWidth, main: main.scrollWidth - main.clientWidth };
  });
  expect(overflow.page).toBeLessThanOrEqual(0);
  expect(overflow.main).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Rules at 400 px');
  await page.getByRole('tab', { name: 'Compare' }).click();
  await expect(page.getByTestId('compare-summary')).toBeVisible();
  await expectAccessible(page, 'Compare at 400 px');
});
