import { Page } from '@playwright/test';
import { DANA, SCREENSHOTS, expect, expectAccessible, goTo, shoot, signIn, test } from './support';

/**
 * F10b: the coverage matrix on the seeded program. This file runs before the suites that change the seed (the
 * Changes re-run resolves the COUN 625 change, after which Utah's UT-7 no longer rests on outdated evidence).
 */
const matrix = (page: Page) => page.getByRole('table', { name: /^Coverage, / });
const row = (page: Page, ref: string) => matrix(page).locator(`tbody tr[data-ref="${ref}"]`);
const state = (page: Page, name: string) => page.getByRole('tab', { name, exact: true });
const card = (page: Page) => page.locator('.lx-cell-card');

async function openCoverage(page: Page): Promise<void> {
  await signIn(page, DANA);
  await goTo(page, 'Coverage');
  await expect(page.getByRole('heading', { level: 1, name: 'Coverage' })).toBeVisible();
  // The first target state opens first (Arizona); the tests start on California.
  await expect(state(page, 'Arizona')).toHaveAttribute('aria-selected', 'true');
  await state(page, 'California').click();
  await expect(page).toHaveURL(/\/coverage\?jurisdiction=CA$/);
  await expect(row(page, 'CA-1')).toBeVisible();
}

/** The index of a cell's column, counted the same way in the header row and in the body. */
async function columnOf(page: Page, code: string): Promise<number> {
  return matrix(page).locator('thead tr').first().locator('th').filter({ hasText: code }).evaluate((th) => (th as HTMLTableCellElement).cellIndex);
}

test('California shows each course carrying the evidence and the flags; Utah shows outdated evidence under COUN 625', async ({ page }) => {
  await openCoverage(page);
  await expect(state(page, 'California')).toHaveAttribute('aria-selected', 'true');
  await expect(state(page, 'California')).toHaveAccessibleDescription(/^\d+ of 8 with no flag$/);

  const ethics = row(page, 'CA-2').getByRole('button', { name: 'COUN 605, evidence from the system, 1 quote' });
  await expect(ethics).toBeVisible();
  await expect(ethics.locator('.lx-mark')).toHaveAttribute('data-kind', 'SYSTEM');
  expect(await ethics.evaluate((b) => (b.closest('td') as HTMLTableCellElement).cellIndex)).toBe(await columnOf(page, 'COUN 605'));
  await expect(row(page, 'CA-2')).toContainText('Rests on one course');
  await expect(row(page, 'CA-6')).toContainText('Rests on an elective');
  await expect(row(page, 'CA-6').getByRole('button', { name: /^COUN 655, / })).toBeVisible();
  await expect(row(page, 'CA-7')).toContainText('No evidence cited');
  await expect(row(page, 'CA-1')).toContainText('Checked by the rules engine');
  await expect(row(page, 'CA-1').getByRole('button')).toHaveCount(0);
  await expect(matrix(page).getByRole('columnheader', { name: 'Elective' })).toBeVisible();

  await state(page, 'Utah').click();
  await expect(page).toHaveURL(/\/coverage\?jurisdiction=UT$/);
  const group = row(page, 'UT-7');
  await expect(group).toContainText('Outdated evidence');
  const outdated = group.getByRole('button', { name: 'COUN 625, outdated evidence from the system, 1 quote on the 2026-27 version' });
  await expect(outdated.locator('.lx-mark')).toHaveAttribute('data-kind', 'OUTDATED');
  await expect(outdated.locator('ng-icon')).toBeVisible();
  expect(await outdated.evaluate((b) => (b.closest('td') as HTMLTableCellElement).cellIndex)).toBe(await columnOf(page, 'COUN 625'));
  await expect(row(page, 'UT-6').getByRole('button', { name: 'Program documents, evidence from the system, accreditation-letter.pdf' })).toBeVisible();

  // The state is in the URL: a reload opens Utah again.
  await page.reload();
  await expect(state(page, 'Utah')).toHaveAttribute('aria-selected', 'true');
  await expect(row(page, 'UT-7')).toContainText('Outdated evidence');
});

test('arrow keys move across the cells, Enter opens the card and its action lands on the requirement in Review', async ({ page }) => {
  await openCoverage(page);
  const cells = matrix(page).getByRole('button');
  await expect(matrix(page).locator('button[tabindex="0"]')).toHaveCount(1);

  // Tab reaches the matrix once, on its first cell; focus opens the card.
  const first = matrix(page).locator('button[tabindex="0"]');
  await first.focus();
  await expect(card(page)).toBeVisible();
  const start = await first.getAttribute('data-pos');
  await page.keyboard.press('ArrowDown');
  const below = page.locator(':focus');
  await expect(below).not.toHaveAttribute('data-pos', start!);
  expect(Number((await below.getAttribute('data-pos'))!.split('-')[0])).toBeGreaterThan(Number(start!.split('-')[0]));
  await expect(matrix(page).locator('button[tabindex="0"]')).toHaveCount(1);
  await expect(below).toHaveAttribute('tabindex', '0');
  await page.keyboard.press('ArrowUp');
  await expect(page.locator(':focus')).toHaveAttribute('data-pos', start!);
  await page.keyboard.press('Control+End');
  await expect(page.locator(':focus')).toHaveAttribute('data-pos', (await cells.last().getAttribute('data-pos'))!);
  await page.keyboard.press('Escape');
  await expect(card(page)).toBeHidden();

  const ethics = row(page, 'CA-2').getByRole('button', { name: /^COUN 605, / });
  await ethics.focus();
  await expect(card(page)).toContainText('COUN 605');
  await expect(card(page)).toContainText('From the system');
  await expect(card(page)).toContainText('1 quote');
  await page.keyboard.press('Enter');
  const action = card(page).getByRole('link', { name: 'Open in Review' });
  await expect(action).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(card(page)).toBeHidden();
  await expect(ethics).toBeFocused();

  // With the pointer: hovering opens the card, its action deep-links to the requirement.
  await row(page, 'CA-2').getByRole('button', { name: /^COUN 605, / }).hover();
  const link = card(page).getByRole('link', { name: 'Open in Review' });
  await expect(link).toHaveAttribute('href', /\/review\?jurisdiction=CA&ref=CA-2$/);
  await link.click();
  await expect(page).toHaveURL(/\/review\?jurisdiction=CA/);
  await expect(page.getByTestId('requirement-heading')).toHaveAttribute('data-ref', 'CA-2');
});

test('a flag chip dims the rows without it and keeps the matrix whole', async ({ page }) => {
  await openCoverage(page);
  const rows = matrix(page).locator('tbody tr');
  const total = await rows.count();
  const chip = page.getByRole('group', { name: 'Highlight a flag' }).getByRole('button', { name: /^Rests on an elective, \d+$/ });
  await chip.click();
  await expect(chip).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('message')).toHaveText(/^Rests on an elective: 1 of 8 requirements highlighted\.$/);
  await expect(rows).toHaveCount(total);
  await expect(row(page, 'CA-6')).not.toHaveClass(/lx-dim/);
  await expect(row(page, 'CA-2')).toHaveClass(/lx-dim/);
  await expect.poll(() => row(page, 'CA-2').locator('.lx-dot').first().evaluate((el) => Number(getComputedStyle(el).opacity))).toBeCloseTo(0.35, 2);
  await expectAccessible(page, 'Coverage with a flag highlighted');

  await chip.click();
  await expect(chip).toHaveAttribute('aria-pressed', 'false');
  await expect(matrix(page).locator('tbody tr.lx-dim')).toHaveCount(0);
});

for (const [width, height] of [[1440, 900], [400, 860]] as const) {
  test(`coverage is accessible in both themes at ${width} px and never scrolls the page sideways`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await openCoverage(page);
    await expectAccessible(page, `Coverage, California, ${width} px`);
    const sideways = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(sideways, 'horizontal page scroll').toBeLessThanOrEqual(0);
    if (width === 400) {
      // Titles wrap to two lines at most (a long word breaks rather than being cut) and every row keeps one height.
      const heights = await matrix(page).locator('tbody tr').evaluateAll((rows) => rows.map((r) => Math.round(r.getBoundingClientRect().height)));
      expect(new Set(heights).size, `row heights ${heights.join(', ')}`).toBe(1);
      const title = row(page, 'CA-6').locator('.lx-req-title');
      expect(await title.evaluate((t) => t.scrollWidth <= t.clientWidth + 1 && t.scrollHeight <= t.clientHeight + 1), 'CA-6 title fits').toBe(true);
    }
    if (SCREENSHOTS) { await shoot(page, `f10b-california-${width}`); }

    await state(page, 'Utah').click();
    await expect(row(page, 'UT-7')).toContainText('Outdated evidence');
    await expectAccessible(page, `Coverage, Utah, ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f10b-utah-${width}`); }

    // Focus brings the cell into view, clear of the sticky columns (the frame's scroll padding).
    const outdated = row(page, 'UT-7').getByRole('button', { name: /^COUN 625, / });
    await outdated.focus();
    await expect(outdated).toBeInViewport();
    expect(await outdated.evaluate((b) => {
      const box = b.getBoundingClientRect();
      return b.contains(document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2));
    }), 'the focused cell is not under a sticky column').toBe(true);
    await expect(card(page)).toContainText('2026-27 version');
    await expectAccessible(page, `Coverage, cell card, ${width} px`);
    if (SCREENSHOTS) { await shoot(page, `f10b-card-${width}`); }
    await page.keyboard.press('Escape');

    await page.getByRole('group', { name: 'Highlight a flag' }).getByRole('button', { name: /^Outdated evidence, \d+$/ }).click();
    await expect(row(page, 'UT-7')).not.toHaveClass(/lx-dim/);
    if (SCREENSHOTS) { await shoot(page, `f10b-filter-${width}`); }
  });
}
