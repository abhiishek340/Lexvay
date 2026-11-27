import { Page } from '@playwright/test';
import { JORDAN, PRIYA, apiPost, allowConsoleErrors, expect, expectAccessible, goTo, nav, openRequirement, signIn, stateTab, switchUser, test } from './support';

const NOTE = 'Please send the passage that covers clinical supervision for this requirement.';

interface ProgramDocument { id: string; filename: string; kind: string; courseCode: string | null; pageCount: number; current: boolean }

async function program(page: Page): Promise<{ programId: string; assessmentId: string }> {
  await page.waitForURL(/\/programs\/[^/]+\//);
  const programId = new URL(page.url()).pathname.split('/')[2];
  const overview = await (await page.request.get(`/api/v1/programs/${programId}/overview`)).json();
  return { programId, assessmentId: overview.assessment.id };
}

test('a reviewer asks for evidence, a contributor answers with a verified passage, and the reviewer accepts it', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });

  // The reviewer requests evidence on a requirement the system found no evidence for.
  await signIn(page, PRIYA);
  const { programId, assessmentId } = await program(page);
  const ut = await (await page.request.get(`/api/v1/assessments/${assessmentId}/mappings?jurisdiction=UT`)).json();
  const missing = (ut.mappings as { id: string; finding: string; method: string; requirement: { ref: string; text: string } }[])
    .find((m) => m.finding === 'EVIDENCE_MISSING' && m.method === 'MATCH')!;
  const ref = missing.requirement.ref;
  await openRequirement(page, 'UT', ref);
  await expect(page.getByTestId('proposed-finding')).toContainText('Evidence missing');
  await page.getByRole('button', { name: 'Add note' }).click();
  await page.getByLabel('Note', { exact: true }).fill(NOTE);
  await page.getByRole('button', { name: 'Request evidence' }).click();
  await expect(page.getByTestId('message')).toContainText(`${ref}: Evidence requested.`);
  await expect(nav(page, 'Evidence requests')).toHaveAccessibleDescription('1 open evidence request');

  // The contributor sees it in the inbox, with the badge.
  await switchUser(page, JORDAN);
  const link = nav(page, 'Evidence requests');
  await expect(link).toHaveAccessibleDescription('1 open evidence request');
  await link.click();
  await expect(page.getByRole('heading', { level: 1, name: 'Evidence requests' })).toBeFocused();
  const table = page.getByRole('table', { name: 'Evidence requests' });
  const row = table.locator(`tr[data-ref="${ref}"]`);
  await expect(row).toContainText(NOTE);
  await expect(row).toContainText(/Dr\. Priya Raman, \d{1,2} [A-Z][a-z]{2}/);
  await expect(row).toContainText('Open');

  // The row detail has the whole note; the table is operated from the keyboard.
  const toggle = row.getByRole('button', { name: `Show details for ${ref}` });
  await toggle.focus();
  await page.keyboard.press('Enter');
  await expect(row.getByRole('button', { name: `Hide details for ${ref}` })).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('.lx-request-detail')).toContainText(NOTE);
  await expectAccessible(page, 'Evidence requests inbox');

  // The answer drawer: the requirement, the document picker and the page.
  const provide = row.getByRole('button', { name: `Provide evidence for ${ref}` });
  await provide.focus();
  await page.keyboard.press('Enter');
  const drawer = page.getByRole('dialog', { name: 'Provide evidence' });
  await expect(drawer).toBeVisible();
  await expect(drawer.getByRole('heading', { name: 'Provide evidence' })).toBeFocused();
  await expect(drawer).toContainText(missing.requirement.text);
  await expect(drawer).toContainText(NOTE);
  await expectAccessible(page, 'Answer drawer');

  const documents: ProgramDocument[] = await (await page.request.get(`/api/v1/programs/${programId}/documents`)).json();
  expect(documents.some((d) => d.kind === 'RULE_TEXT')).toBe(false);
  const syllabus = documents.find((d) => d.kind === 'SYLLABUS' && d.current && d.pageCount > 0)!;
  const earlier = documents.find((d) => !d.current)!;
  const picker = drawer.getByRole('combobox', { name: 'Program document' });
  await picker.click();
  await expect(page.getByRole('option', { name: new RegExp(`^${earlier.filename}`) })).toHaveCount(0);
  await page.getByRole('option', { name: `${syllabus.filename} syllabus`, exact: true }).click();
  await expect(picker).toContainText(syllabus.filename);

  // Earlier catalog years appear on request, labelled with their year.
  await drawer.getByLabel('Include earlier catalog years').check();
  await picker.click();
  await expect(page.getByRole('option', { name: new RegExp(`^${earlier.filename} syllabus, catalog year`) })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(drawer).toBeVisible();

  // A real passage chosen from the keyboard with the character range dialog.
  const stored = await (await page.request.get(`/api/v1/documents/${syllabus.id}/pages/1`)).json();
  // The first sentence of at least 30 characters on the page, exactly as stored.
  const sentence = /[A-Z][^.\n]{30,}\./.exec(stored.text)!;
  const start = sentence.index;
  const end = start + sentence[0].length;
  const quote = stored.text.slice(start, end);
  const viewer = drawer.locator('lx-document-viewer');
  await expect(viewer.locator('pre')).toHaveText(stored.text);
  const rangeButton = viewer.getByRole('button', { name: 'Select passage by character range' });
  const range = page.getByRole('dialog', { name: 'Select passage by character range' });
  // Escape closes only the range dialog, not the drawer under it.
  await rangeButton.focus();
  await page.keyboard.press('Enter');
  await expect(range).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(range).toBeHidden();
  await expect(drawer).toBeVisible();
  await rangeButton.focus();
  await page.keyboard.press('Enter');
  // Typed key by key, as a keyboard user enters them.
  await range.getByLabel('Start offset').pressSequentially(String(start));
  await range.getByLabel('End offset').pressSequentially(String(end));
  await expect(range.getByTestId('range-quote')).toHaveText(quote);
  await range.getByRole('button', { name: 'Use this passage' }).focus();
  await page.keyboard.press('Enter');
  await expect(range).toBeHidden();
  await expect(drawer).toBeVisible();
  const passage = drawer.getByTestId('selected-passage');
  await expect(passage.locator('blockquote')).toHaveText(quote);
  await expect(passage).toContainText(`Page 1, characters ${start}–${end}`);
  await drawer.getByLabel('Comment (optional)').fill('Week 2 of the syllabus covers this.');
  await expectAccessible(page, 'Answer drawer with a selected passage');

  // A tampered quote is refused by the server, through the API and through the screen, with the server's words.
  const tampered = await apiPost(page, `/api/v1/mappings/${missing.id}/evidence-responses`,
    { documentId: syllabus.id, pageNumber: 1, quote: 'This course covers clinical supervision in depth.', comment: null });
  expect(tampered.status()).toBe(400);
  const detail: string = (await tampered.json()).detail;
  expect(detail).toContain('not found on page 1');
  // Chromium logs the 400 the tampered submission through the screen is answered with.
  allowConsoleErrors(page, /status of 400.*\/api\/v1\/mappings\/[^/]+\/evidence-responses/);
  await page.route('**/evidence-responses', async (route) => {
    const body = route.request().postDataJSON();
    await route.continue({ postData: JSON.stringify({ ...body, quote: 'This course covers clinical supervision in depth.' }) });
  }, { times: 1 });
  await drawer.getByRole('button', { name: 'Submit evidence' }).click();
  await expect(drawer.getByTestId('quote-error')).toHaveText(detail);
  await expect(drawer).toBeVisible();

  // The real passage goes through: toast, the drawer closes, the row moves to Answered.
  await drawer.getByRole('button', { name: 'Submit evidence' }).click();
  await expect(page.locator('.p-toast')).toContainText('Evidence sent to review');
  await expect(drawer).toBeHidden();
  await expect(page.getByRole('heading', { level: 1, name: 'Evidence requests' })).toBeFocused();
  await expect(page.getByTestId('requests-empty')).toContainText('No open evidence requests.');
  await expect(nav(page, 'Evidence requests')).not.toHaveAttribute('aria-describedby');
  await page.locator('p-selectbutton').getByText('Answered').click();
  await expect(row).toContainText('Answered');
  await expect(row).toContainText('With the reviewer');
  await expect(row).toContainText(new RegExp(`Jordan Ellis, \\d{1,2} [A-Z][a-z]{2}, ${syllabus.filename}, page 1`));
  await expect(row.getByRole('button', { name: /Provide evidence/ })).toHaveCount(0);
  await expectAccessible(page, 'Evidence requests, answered');

  // The reviewer sees the requirement back in "Needs review" with the provided evidence, and accepts it.
  await switchUser(page, PRIYA);
  await goTo(page, 'Review');
  await stateTab(page, 'UT').click();
  await page.getByRole('region', { name: 'Requirements' }).getByRole('button', { name: / to review$/ }).click();
  const listed = page.getByRole('region', { name: 'Requirements' }).getByRole('button').filter({ hasText: new RegExp(`^${ref}(?!\\d)`) });
  await expect(listed).toContainText('evidence provided');
  await listed.click();
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', ref);
  await expect(page.getByTestId('proposed-finding')).toContainText('Evidence missing');
  const provided = page.getByTestId('provided-evidence');
  await expect(provided).toHaveCount(1);
  await expect(provided.getByRole('heading')).toHaveText(/Evidence provided by Jordan Ellis, \d{1,2} [A-Z][a-z]{2} \d{4}/);
  await expect(provided.getByTestId('evidence-mark')).toHaveText(quote);
  await expect(provided).toContainText('Week 2 of the syllabus covers this.');
  await expect(page.getByLabel('Note', { exact: true })).toHaveAttribute('placeholder', 'Note the evidence you relied on');
  await expectAccessible(page, 'Review with provided evidence');
  await page.getByLabel('Note', { exact: true }).fill('The syllabus passage answers it.');
  await page.getByRole('button', { name: 'Accept evidence' }).click();
  await expect(page.getByTestId('message')).toContainText(`${ref}: Accepted.`);

  // Nothing is open any more; the provided evidence stays on the requirement.
  await goTo(page, 'Evidence requests');
  await expect(page.getByTestId('requests-empty')).toContainText('No open evidence requests.');
  await openRequirement(page, 'UT', ref);
  await expect(page.getByTestId('provided-evidence').getByTestId('evidence-mark')).toHaveText(quote);
});

test('at 400 px: the inbox, the drawer, a blocked request and the provided evidence', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  const { assessmentId } = await program(page);
  const ut = await (await page.request.get(`/api/v1/assessments/${assessmentId}/mappings?jurisdiction=UT`)).json();
  const mappings = ut.mappings as { id: string; finding: string; decision: unknown; responseCount: number; requirement: { ref: string } }[];
  const ambiguous = mappings.find((m) => m.finding === 'AMBIGUOUS' && m.decision === null)!;
  expect((await apiPost(page, `/api/v1/mappings/${ambiguous.id}/reviews`, { kind: 'EVIDENCE_REQUESTED', note: 'Please send the required-course coverage.' })).status()).toBe(201);

  await switchUser(page, JORDAN);
  await goTo(page, 'Evidence requests');
  const row = page.getByRole('table', { name: 'Evidence requests' }).locator(`tr[data-ref="${ambiguous.requirement.ref}"]`);
  await expect(row).toContainText('Open');
  // A card has no column headers, so its answer count is said in words.
  await expect(row.getByTestId('answer-words')).toBeVisible();
  await expect(row.getByTestId('answer-words')).toHaveText('No answers');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Evidence requests at 400 px');

  // Under 1024 px the drawer takes the full width; Escape closes it and focus returns to the row action.
  const provide = row.getByRole('button', { name: `Provide evidence for ${ambiguous.requirement.ref}` });
  await provide.click();
  const drawer = page.getByRole('dialog', { name: 'Provide evidence' });
  await expect(drawer).toBeVisible();
  await expect.poll(async () => (await drawer.boundingBox())?.width).toBe(400);
  await expectAccessible(page, 'Answer drawer at 400 px');
  await page.keyboard.press('Escape');
  await expect(drawer).toBeHidden();
  await expect(provide).toBeFocused();

  // A request whose mapping went stale after a change is blocked, and so is one a signed determination covers (FIX-3).
  // The seed cannot make a requested mapping stale or signed without a change that other suites depend on, so the list
  // response carries the flags here; the flags themselves are covered by the backend tests.
  let flag: 'stale' | 'signed' = 'stale';
  await page.route('**/evidence-requests?status=ALL', async (route) => {
    const response = await route.fetch();
    const rows = (await response.json()) as { mappingId: string }[];
    await route.fulfill({ response, json: rows.map((r) => (r.mappingId === ambiguous.id ? { ...r, [flag]: true } : r)) });
  });
  const ref = ambiguous.requirement.ref;
  const reasons = [
    ['stale', 'Waiting for a re-run', 'This requirement is waiting for a re-run after a change; answer it after the re-run.'],
    ['signed', 'Signed', 'A signed determination already covers this requirement, so the request can no longer be answered.'],
  ] as const;
  for (const [reason, short, why] of reasons) {
    flag = reason;
    // Leave the screen and come back, so it fetches the list again; Review must have opened first, or the second click
    // is a navigation to the screen already shown, which the router ignores.
    await goTo(page, 'Review');
    await expect(nav(page, 'Review')).toHaveAttribute('aria-current', 'page');
    await goTo(page, 'Evidence requests');
    await expect(row).toContainText('Blocked');
    await expect(row.getByTestId('blocked-reason')).toContainText(short);
    await expect(row.getByRole('button', { name: /Provide evidence/ })).toHaveCount(0);
    await row.getByRole('button', { name: `Why ${ref} is blocked` }).click();
    await expect(page.getByRole('dialog', { name: `Why ${ref} is blocked` })).toHaveText(why);
    await expectAccessible(page, `Evidence requests at 400 px, blocked (${reason})`);
    await page.keyboard.press('Escape');
  }
  // A signed request is listed, blocked, but not counted in the badge: it can never be answered.
  await expect(nav(page, 'Evidence requests')).not.toHaveAttribute('aria-describedby');
  await page.unroute('**/evidence-requests?status=ALL');

  // The provided evidence of the first test, on a phone: behind "Show on page" like the system's.
  const answered = mappings.find((m) => m.responseCount > 0)!;
  await openRequirement(page, 'UT', answered.requirement.ref);
  const provided = page.getByTestId('provided-evidence');
  await expect(provided.getByRole('heading')).toContainText('Evidence provided by Jordan Ellis');
  const show = provided.getByRole('button', { name: 'Show on page' });
  await expect(show).toHaveAttribute('aria-expanded', 'false');
  await show.click();
  await expect(show).toHaveAttribute('aria-expanded', 'true');
  await expect(provided.getByTestId('evidence-mark')).toBeFocused();
  await expectAccessible(page, 'Review at 400 px with provided evidence');
});
