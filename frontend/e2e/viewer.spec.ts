import { softWrap } from '../src/app/shared/document-viewer/page-text';
import { PRIYA, citedRequirement, expect, expectAccessible, openRequirement, signIn, test } from './support';

test('the review screen shows the cited page with the quote marked, pages through it, and opens the original', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await signIn(page, PRIYA);
  const { ref, evidence } = await citedRequirement(page, 'UT');
  await openRequirement(page, 'UT', ref);

  const viewer = page.locator('lx-document-viewer');
  const mark = viewer.getByTestId('evidence-mark');
  const pageText = await (await page.request.get(`/api/v1/documents/${evidence.documentId}/pages/${evidence.pageNumber}`)).json();
  // The page reads as reflowed text: line breaks that only wrap a sentence show as spaces, one character for one, so
  // the shown text has the stored length and the mark sits at the stored offsets around exactly the verified quote.
  const shown = softWrap(pageText.text);
  expect(await viewer.locator('pre').textContent()).toBe(shown);
  expect(shown.length).toBe(pageText.text.length);
  expect(shown).not.toBe(pageText.text);
  expect(await mark.textContent()).toBe(shown.slice(evidence.charStart, evidence.charEnd));
  expect(pageText.text.slice(evidence.charStart, evidence.charEnd)).toBe(evidence.quote);
  await expect(mark).toHaveText(evidence.quote);
  await expect(mark).toBeInViewport();
  await expect(viewer.locator('mark')).toHaveCount(1);
  // In the wide layout the viewer is always on screen, so it does not take focus from the list.
  await expect(mark).not.toBeFocused();

  // The quote alone stays available in a collapsed disclosure.
  const quoteOnly = page.locator('details.lx-quote-only');
  await expect(quoteOnly).not.toHaveAttribute('open', '');
  await quoteOnly.locator('summary').click();
  await expect(page.getByTestId('evidence-quote')).toHaveText(evidence.quote);

  await expectAccessible(page, 'Review with the document viewer');

  // Previous and next page; the position is a polite status, so the change is announced.
  const position = viewer.getByRole('status');
  await expect(position).toHaveAttribute('aria-live', 'polite');
  await expect(position).toHaveText(`Page ${evidence.pageNumber} of ${pageText.pageCount}`);
  const previous = viewer.getByRole('button', { name: 'Previous page' });
  const next = viewer.getByRole('button', { name: 'Next page' });
  expect(pageText.pageCount).toBeGreaterThan(evidence.pageNumber);
  await expect(previous).toBeDisabled();
  await next.focus();
  await page.keyboard.press('Enter');
  await expect(position).toHaveText(`Page ${evidence.pageNumber + 1} of ${pageText.pageCount}`);
  await expect(viewer.locator('mark')).toHaveCount(0);
  const nextPage = await (await page.request.get(`/api/v1/documents/${evidence.documentId}/pages/${evidence.pageNumber + 1}`)).json();
  expect(await viewer.locator('pre').textContent()).toBe(softWrap(nextPage.text));
  if (evidence.pageNumber + 1 === pageText.pageCount) {
    // Next is now disabled at the last page, and focus moved to Previous instead of falling to the page.
    await expect(next).toBeDisabled();
    await expect(previous).toBeFocused();
  }
  await previous.click();
  await expect(position).toHaveText(`Page ${evidence.pageNumber} of ${pageText.pageCount}`);
  await expect(mark).toHaveText(evidence.quote);

  // "Open original" opens the stored file in a new tab without an opener.
  const original = viewer.getByRole('link', { name: 'Open original (opens in a new tab)' });
  await expect(original).toHaveAttribute('target', '_blank');
  await expect(original).toHaveAttribute('rel', 'noopener');
  const file = await page.request.get((await original.getAttribute('href'))!);
  expect(file.status()).toBe(200);
  expect(file.headers()['content-type']).toBe('application/pdf');
  expect(file.headers()['content-disposition']).toMatch(/^inline;/);
});

test('under 1024 px the viewer waits behind "Show on page" and takes focus when opened', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 860 });
  await signIn(page, PRIYA);
  const { ref, evidence } = await citedRequirement(page, 'CO');
  await openRequirement(page, 'CO', ref);

  const show = page.getByRole('button', { name: 'Show on page' });
  await expect(show).toHaveAttribute('aria-expanded', 'false');
  await expect(page.locator('lx-document-viewer')).toHaveCount(0);
  await expect(page.locator('details.lx-quote-only summary')).toHaveText('Quote only');
  await expectAccessible(page, 'Review at 400 px, viewer collapsed');

  await show.click();
  await expect(show).toHaveAttribute('aria-expanded', 'true');
  const mark = page.getByTestId('evidence-mark');
  await expect(mark).toHaveText(evidence.quote);
  await expect(mark).toBeFocused();
  await expect(mark).toBeInViewport();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await expectAccessible(page, 'Review at 400 px with the document viewer');
});

test('a passage can be chosen by mouse selection and by character range from the keyboard', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await signIn(page, PRIYA);
  const { programId, evidence } = await citedRequirement(page, 'AZ');
  const stored = await (await page.request.get(`/api/v1/documents/${evidence.documentId}/pages/${evidence.pageNumber}`)).json();
  await page.goto(`/programs/${programId}/dev/document-viewer?document=${evidence.documentId}&page=${evidence.pageNumber}`);
  const viewer = page.locator('lx-document-viewer');
  await expect(viewer.locator('pre')).toHaveText(stored.text);
  expect(await viewer.locator('pre').textContent()).toBe(softWrap(stored.text));
  const chosen = page.getByTestId('selection');

  // Mouse: select a stretch of the shown text across a line break shown as a space; the toolbar offers the passage,
  // quoted from the stored text by the same offsets, so the line break is still in it.
  const joined = softWrap(stored.text).split('').findIndex((c, i) => c !== stored.text[i]);
  expect(joined).toBeGreaterThan(0);
  const [from, to] = [Math.max(0, joined - 20), joined + 20];
  await viewer.locator('pre').evaluate((pre, [start, end]) => {
    const text = Array.from(pre.childNodes).find((n) => n.nodeType === Node.TEXT_NODE)!;
    const range = document.createRange();
    range.setStart(text, start);
    range.setEnd(text, end);
    document.getSelection()!.removeAllRanges();
    document.getSelection()!.addRange(range);
    pre.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  }, [from, to]);
  await expect(chosen).toHaveAttribute('data-start', String(from));
  await expect(chosen).toHaveAttribute('data-end', String(to));
  expect(await chosen.textContent()).toBe(stored.text.slice(from, to));
  expect(stored.text.slice(from, to)).toContain('\n');
  const toolbar = page.getByRole('toolbar', { name: 'Selected passage' });
  await expect(toolbar).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(toolbar).toBeHidden();

  // Keyboard: the character range dialog shows the quote live and emits the same event.
  const rangeButton = viewer.getByRole('button', { name: 'Select passage by character range' });
  await rangeButton.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Select passage by character range' });
  await expect(dialog).toBeVisible();
  const use = dialog.getByRole('button', { name: 'Use this passage' });
  await expect(use).toBeDisabled();
  await dialog.getByLabel('Start offset').fill(String(evidence.charStart));
  await dialog.getByLabel('End offset').fill(String(evidence.charEnd));
  await expect(dialog.getByTestId('range-quote')).toHaveText(evidence.quote);
  await expectAccessible(page, 'Character range dialog');
  await use.focus();
  await page.keyboard.press('Enter');
  await expect(dialog).toBeHidden();
  await expect(chosen).toHaveText(evidence.quote);
  await expect(chosen).toHaveAttribute('data-start', String(evidence.charStart));
  await expect(chosen).toHaveAttribute('data-end', String(evidence.charEnd));
  await expect(chosen).toHaveAttribute('data-page', String(evidence.pageNumber));
  await expectAccessible(page, 'Document viewer, selectable');
});
