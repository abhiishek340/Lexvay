import { Page } from '@playwright/test';
import { JORDAN, PASSWORD, PRIYA, SCREENSHOTS, expect, expectAccessible, nav, shoot, test } from './support';

/*
 * Program setup (F2) against the seeded Northfield tenant: a contributor creates a fictional program for two states
 * whose rules the seed approves (Arizona and California), adds two courses, uploads two fictional syllabi (PDF and
 * DOCX) and waits for them to be read, records total credits from a passage chosen with the keyboard, sees readiness
 * clear, runs the first assessment and reaches Review; then a reviewer sees the same setup read-only.
 *
 * The fixture syllabi share no word with Arizona's or California's requirements (fixtures/docs, D-35x), so no
 * requirement reaches the scripted model, which holds no match script for this program's documents: the run finishes
 * with the rules engine's results and "no candidate passages" for the rest, exactly as the product would.
 */

const NAME = 'Demo M.A. School Counseling (fictional)';
const LICENSE = 'CLINICAL_MENTAL_HEALTH_COUNSELING';

function step(page: Page, name: RegExp) {
  return page.getByRole('list', { name: 'Setup steps' }).getByRole('button', { name });
}

/** Both themes at 1440 and 400 px, then back to the size the test runs at. */
async function capture(page: Page, name: string): Promise<void> {
  if (!SCREENSHOTS) { return; }
  const size = page.viewportSize();
  for (const width of [1440, 400]) {
    await page.setViewportSize({ width, height: width === 1440 ? 900 : 860 });
    await shoot(page, `ui2-4-${name}-${width}`);
  }
  if (size) { await page.setViewportSize(size); }
}

/**
 * Signs in and opens the seeded program. Once this spec has created its program the home screen lists programs instead
 * of opening the only one, so the shared signIn (which expects the rail at once) does not fit here.
 */
async function signInTo(page: Page, email: string, program: string): Promise<void> {
  await page.context().clearCookies();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  const list = page.getByRole('link', { name: program }).last();
  await expect(page.getByRole('navigation', { name: 'Screens' }).or(list).first()).toBeVisible();
  if (await list.isVisible()) { await list.click(); }
  await expect(page.getByRole('navigation', { name: 'Screens' })).toBeVisible();
}

async function expectNoPageScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

test('a contributor sets up a program from nothing to its first assessment', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 1280, height: 900 });
  await signInTo(page, JORDAN, 'M.S. Clinical Mental Health Counseling');

  // The program switcher's footer starts a new program.
  await page.getByRole('combobox', { name: /^Program, / }).click();
  await page.getByRole('link', { name: 'New program' }).click();
  await expect(page).toHaveURL(/\/programs\/new$/);
  await expect(page.getByRole('heading', { level: 1, name: 'New program' })).toBeVisible();
  // Before the program exists only the two steps that create it are open.
  await expect(step(page, /^3\. Courses/)).toBeDisabled();
  await expect(step(page, /^6\. Readiness/)).toBeDisabled();
  await expectAccessible(page, 'New program, details');

  // Details are checked before moving on.
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('Enter the program name.')).toBeVisible();
  await page.getByRole('textbox', { name: 'Name', exact: true }).fill(NAME);
  // The license types the tenant's rules use are offered; another can be typed.
  await page.getByRole('combobox', { name: 'License type' }).fill(LICENSE.slice(0, 8));
  await page.getByRole('option', { name: LICENSE }).click();
  await expect(page.getByRole('combobox', { name: 'License type' })).toHaveValue(LICENSE);
  await page.getByRole('textbox', { name: 'Catalog year', exact: true }).fill('2026-27');
  await capture(page, 'setup-details');
  await page.getByRole('button', { name: 'Next' }).click();

  // States: at least one, chosen in the MultiSelect, drawn on the mini map.
  await expect(step(page, /^2\. States/)).toHaveAttribute('aria-current', 'step');
  await page.getByRole('button', { name: 'Create program' }).click();
  await expect(page.getByTestId('states-error')).toHaveText('Choose at least one state.');
  await page.getByTestId('states-select').click();
  for (const state of ['Arizona (AZ)', 'California (CA)']) {
    await page.getByRole('searchbox').fill(state.slice(0, 5));
    await page.getByRole('option', { name: state }).click();
  }
  await page.keyboard.press('Escape');
  await expect(page.getByText('2 of 51 chosen')).toBeVisible();
  await expect(step(page, /^2\. States, 2 chosen/)).toBeVisible();
  await expectAccessible(page, 'New program, states');
  await capture(page, 'setup-states');
  await page.getByRole('button', { name: 'Create program' }).click();

  // Created: the URL is the program's own (replaced, not pushed), and setup goes on to its courses.
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]{36}\/setup$/);
  const programId = new URL(page.url()).pathname.split('/')[2];
  await expect(page.getByTestId('message')).toContainText(`${NAME} created with 2 states.`);
  await expect(page.getByRole('heading', { level: 1, name: 'Program setup' })).toBeVisible();
  await expect(step(page, /^3\. Courses/)).toHaveAttribute('aria-current', 'step');
  await expect(nav(page, 'Program setup')).toHaveAttribute('aria-current', 'page');

  // Courses: two, one required and one elective; the first catalog year is the program's.
  for (const [code, title, required] of [['SCHC 501', 'Foundations of School Guidance', true], ['SCHC 520', 'School Guidance Seminar', false]] as const) {
    await page.getByRole('button', { name: 'Add course' }).click();
    const dialog = page.getByRole('dialog', { name: 'Add course' });
    await expect(dialog.getByTestId('course-year')).toHaveText('2026-27');
    await dialog.getByLabel('Code').fill(code);
    await dialog.getByLabel('Title').fill(title);
    if (!required) { await dialog.getByRole('switch', { name: 'Required course' }).uncheck(); }
    if (code === 'SCHC 501') { await expectAccessible(page, 'Add course dialog'); }
    await dialog.getByRole('button', { name: 'Add course' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('table', { name: 'Courses' }).locator(`tr[data-code="${code}"]`)).toContainText(required ? 'Required' : 'Elective');
  }
  await expect(step(page, /^3\. Courses, 2 courses, done/)).toBeVisible();
  await expectAccessible(page, 'Courses');
  await capture(page, 'setup-courses');

  // Documents: each syllabus into its course, sent one after the other, followed until read.
  await step(page, /^4\. Documents/).click();
  const files = page.locator('p-fileupload input[type="file"]');
  for (const [course, file] of [['SCHC 501 Foundations of School Guidance', 'e2e/fixtures/schc501-syllabus.pdf'], ['SCHC 520 School Guidance Seminar', 'e2e/fixtures/schc520-syllabus.docx']]) {
    // A Select opened while the previous one's overlay is still leaving can stay empty for a moment; open it again then.
    await expect(async () => {
      await page.getByTestId('upload-target').click();
      await page.getByRole('option', { name: course }).click({ timeout: 2000 });
    }).toPass();
    await expect(page.getByTestId('upload-target')).toContainText(course);
    await files.setInputFiles(file);
  }
  const queue = page.getByRole('list', { name: 'Files to upload' });
  await expect(queue.locator('li')).toHaveCount(2);
  await expect(queue.getByRole('combobox', { name: 'Course of schc501-syllabus.pdf' })).toContainText('SCHC 501, 2026-27');
  await expect(queue.getByRole('combobox', { name: 'Kind of schc520-syllabus.docx' })).toContainText('Syllabus');
  await expectAccessible(page, 'Documents, files chosen');
  await page.getByRole('button', { name: 'Upload 2 files' }).click();
  for (const file of ['schc501-syllabus.pdf', 'schc520-syllabus.docx']) {
    await expect(queue.locator(`li[data-file="${file}"]`).getByTestId('upload-state')).toContainText('Ready (1 page)', { timeout: 30_000 });
  }
  await expect(step(page, /^4\. Documents, 2 documents, done/)).toBeVisible();
  await expectAccessible(page, 'Documents, read');
  await capture(page, 'setup-documents');

  // The same file again is refused as a copy, with a way to the document the program already holds.
  await files.setInputFiles('e2e/fixtures/schc520-syllabus.docx');
  await page.getByRole('button', { name: 'Upload 1 file' }).click();
  await expect(queue.locator('li[data-state="DUPLICATE"]')).toContainText('Already uploaded');
  await queue.locator('li[data-state="DUPLICATE"]').getByRole('button', { name: 'Open schc520-syllabus.docx' }).click();
  const sheet = page.getByRole('dialog', { name: 'schc520-syllabus.docx' });
  await expect(sheet.locator('lx-document-viewer pre')).toContainText('School Guidance Seminar');
  await page.keyboard.press('Escape');
  await expect(sheet).toBeHidden();

  // Program facts: the numbers Arizona's and California's approved rules check.
  await step(page, /^5\. Program facts/).click();
  const facts = page.getByRole('list', { name: 'Program facts' });
  await expect(facts.locator('li[data-field]')).toHaveCount(3);
  await expect(facts.locator('li[data-field="TOTAL_CREDITS"]')).toContainText('Not recorded');
  await expectAccessible(page, 'Program facts');
  await facts.getByRole('button', { name: 'Record Total credits' }).click();
  const record = page.getByRole('dialog', { name: 'Record total credits' });
  await expect(record.getByRole('heading', { name: 'Record total credits' })).toBeFocused();
  await record.getByLabel('Value').pressSequentially('60');
  await record.getByRole('combobox', { name: 'Document' }).click();
  await page.getByRole('option', { name: 'schc501-syllabus.pdf syllabus', exact: true }).click();

  // The passage, typed as offsets in the keyboard range dialog, exactly as stored on the page.
  const documents = await (await page.request.get(`/api/v1/programs/${programId}/documents`)).json() as { id: string; filename: string }[];
  const syllabus = documents.find((d) => d.filename === 'schc501-syllabus.pdf')!;
  const stored = await (await page.request.get(`/api/v1/documents/${syllabus.id}/pages/1`)).json();
  const sentence = 'The degree requires the completion of 60 semester credit hours.';
  const start = (stored.text as string).indexOf(sentence);
  expect(start).toBeGreaterThanOrEqual(0);
  await record.getByRole('button', { name: 'Select passage by character range' }).click();
  const range = page.getByRole('dialog', { name: 'Select passage by character range' });
  await range.getByLabel('Start offset').pressSequentially(String(start));
  await range.getByLabel('End offset').pressSequentially(String(start + sentence.length));
  await expect(range.getByTestId('range-quote')).toHaveText(sentence);
  await range.getByRole('button', { name: 'Use this passage' }).focus();
  await page.keyboard.press('Enter');
  await expect(record.getByTestId('fact-passage').locator('blockquote')).toHaveText(sentence);
  await expect(record.getByTestId('fact-mismatch')).toHaveCount(0);
  await expectAccessible(page, 'Record a fact');
  await capture(page, 'setup-fact-sheet');
  await record.getByRole('button', { name: 'Record fact' }).click();
  await expect(record).toBeHidden();
  await expect(facts.locator('li[data-field="TOTAL_CREDITS"]').getByTestId('fact-value')).toHaveText('60');
  await expect(facts.locator('li[data-field="TOTAL_CREDITS"]')).toContainText(sentence);
  await expect(step(page, /^5\. Program facts, 2 missing/)).toBeVisible();
  await capture(page, 'setup-facts');

  // Readiness: nothing blocks; the two hours not recorded are a warning that says what will happen.
  await step(page, /^6\. Readiness/).click();
  const checks = page.getByRole('list', { name: 'Readiness checks' });
  await expect(checks.locator('[data-check="RULES"]')).toHaveAttribute('data-tone', 'found');
  await expect(checks.locator('[data-check="COURSE_DOCUMENTS"]')).toHaveAttribute('data-tone', 'found');
  await expect(checks.locator('[data-check="READING"]')).toHaveAttribute('data-tone', 'found');
  await expect(checks.locator('[data-check="FACTS"]')).toHaveAttribute('data-tone', 'ambiguous');
  await expect(checks.locator('[data-check="FACTS"]')).toContainText('the rules engine will report them as missing evidence');
  await expect(step(page, /^6\. Readiness, Ready/)).toBeVisible();
  await expectAccessible(page, 'Readiness');
  await capture(page, 'setup-readiness');

  await page.getByRole('button', { name: 'Run assessment' }).click();
  const progress = page.getByTestId('assessment-progress');
  await expect(progress).toHaveAttribute('data-status', 'FINISHED', { timeout: 60_000 });
  await expect(progress).toContainText('Assessment finished');
  await expect(page.locator('.p-toast')).toContainText('Assessment finished');
  await capture(page, 'setup-assessed');
  await progress.getByRole('link', { name: 'Open Review' }).click();
  await expect(page).toHaveURL(new RegExp(`/programs/${programId}/review`));
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', /^(AZ|CA)-/);
});

test('a reviewer sees the setup read-only, and it fits 400 px', async ({ page }) => {
  // Two programs now: the home screen lists them instead of opening the only one.
  await signInTo(page, PRIYA, NAME);
  const programId = new URL(page.url()).pathname.split('/')[2];
  await nav(page, 'Program setup').click();
  await expect(page).toHaveURL(new RegExp(`/programs/${programId}/setup$`));
  await expect(page.getByRole('heading', { level: 1, name: 'Program setup' })).toBeVisible();
  for (const name of [/^1\. Details/, /^3\. Courses/, /^4\. Documents/, /^5\. Program facts/]) {
    await step(page, name).click();
    await expect(page.getByRole('button', { name: /^(Add course|New catalog year|Choose files|Record|Next|Create program)/ })).toHaveCount(0);
  }
  await expect(page.locator('p-fileupload')).toHaveCount(0);
  // Starting an assessment stays open to reviewers.
  await step(page, /^6\. Readiness/).click();
  await expect(page.getByRole('button', { name: 'Run assessment' })).toBeEnabled();
  await expectAccessible(page, 'Setup, read-only');

  await page.setViewportSize({ width: 400, height: 860 });
  for (const name of [/^3\. Courses/, /^4\. Documents/, /^5\. Program facts/, /^6\. Readiness/]) {
    await step(page, name).click();
    await expectNoPageScroll(page);
  }
  await expectAccessible(page, 'Setup at 400 px');

  // A reviewer cannot start a new program: the address leads home.
  await page.goto('/programs/new');
  await expect(page).not.toHaveURL(/\/programs\/new$/);
});
