import { describe, expect, it } from 'vitest';
import { CoverageMatrix, CoverageRequirement } from '../../core/models';
import { FLAGS, FLAG_ORDER, PROGRAM_COLUMN, columnGroups, coverageView, firstCell, flagCounts, matchesFlag, move, unflaggedShare } from './coverage-view';

const course = (id: string, code: string, required: boolean) => ({ courseId: `c-${id}`, courseVersionId: id, code, title: `${code} title`, catalogYear: '2027-28', required });

function requirement(ref: string, over: Partial<CoverageRequirement> = {}): CoverageRequirement {
  return { mappingId: `m-${ref}`, ref, title: `${ref} title`, type: 'SUBJECT', finding: 'EVIDENCE_FOUND', decision: null, cells: [], outdatedCells: [], programLevel: [], risk: 'NONE', ...over };
}

/** Three required courses and one elective, as the API orders them (required first, then by code). */
const MATRIX: CoverageMatrix = {
  program: { id: 'p1', name: 'Program' }, jurisdiction: { code: 'UT', name: 'Utah' }, assessmentId: 'a1', ruleVersion: { id: 'rv1', label: 'Utah 2026' },
  courses: [course('v605', 'COUN 605', true), course('v610', 'COUN 610', true), course('v625', 'COUN 625', true), course('v655', 'COUN 655', false)],
  requirements: [
    requirement('UT-1', { type: 'NUMERIC' }),
    requirement('UT-2', { cells: [{ courseVersionId: 'v605', source: 'SYSTEM', quoteCount: 1 }], risk: 'SINGLE_COURSE' }),
    requirement('UT-3', {
      cells: [{ courseVersionId: 'v605', source: 'SYSTEM', quoteCount: 1 }, { courseVersionId: 'v610', source: 'SYSTEM', quoteCount: 1 }, { courseVersionId: 'v610', source: 'PERSON', quoteCount: 2 }],
    }),
    requirement('UT-4', { finding: 'AMBIGUOUS', cells: [{ courseVersionId: 'v655', source: 'PERSON', quoteCount: 1 }], risk: 'ELECTIVE_ONLY' }),
    requirement('UT-5', { finding: 'EVIDENCE_MISSING', risk: 'NO_EVIDENCE' }),
    requirement('UT-6', { programLevel: [{ documentId: 'd1', filename: 'accreditation-letter.pdf', kind: 'ACCREDITATION', source: 'SYSTEM' }] }),
    requirement('UT-7', { outdatedCells: [{ courseVersionId: 'v625-old', code: 'COUN 625', catalogYear: '2026-27', supersededBy: 'v625', source: 'SYSTEM', quoteCount: 1 }], risk: 'OUTDATED_EVIDENCE' }),
    requirement('UT-8', { type: 'SUPERVISED_HOURS' }),
  ],
};

describe('coverageView', () => {
  const view = coverageView(MATRIX);
  const row = (ref: string) => view.rows.find((r) => r.requirement.ref === ref)!;
  const col = (code: string) => view.columns.findIndex((c) => c.code === code);

  it('keeps the courses in the order the API gives and puts the program column last', () => {
    expect(view.columns.map((c) => c.code)).toEqual(['COUN 605', 'COUN 610', 'COUN 625', 'COUN 655', 'Program']);
    expect(view.columns.map((c) => c.index)).toEqual([0, 1, 2, 3, 4]);
    expect(view.columns.at(-1)).toMatchObject({ key: PROGRAM_COLUMN, required: null });
    expect(view.groups).toEqual([{ required: true, span: 3 }, { required: false, span: 1 }]);
  });

  it('groups adjacent columns by required, even when the two kinds alternate', () => {
    expect(columnGroups([course('a', 'A', true), course('b', 'B', false), course('c', 'C', true)]))
      .toEqual([{ required: true, span: 1 }, { required: false, span: 1 }, { required: true, span: 1 }]);
    expect(columnGroups([])).toEqual([]);
  });

  it('keeps the requirements in ordinal order, one cell slot per column', () => {
    expect(view.rows.map((r) => r.requirement.ref)).toEqual(['UT-1', 'UT-2', 'UT-3', 'UT-4', 'UT-5', 'UT-6', 'UT-7', 'UT-8']);
    for (const r of view.rows) { expect(r.cells).toHaveLength(view.columns.length); }
  });

  it('looks each cell up by course version and names it in words', () => {
    const ethics = row('UT-2').cells;
    expect(ethics[col('COUN 605')]).toMatchObject({ kind: 'SYSTEM', system: 1, person: 0, name: 'COUN 605, evidence from the system, 1 quote' });
    expect(ethics.filter((c) => c !== null)).toHaveLength(1);
    expect(row('UT-3').cells[col('COUN 610')]).toMatchObject({
      kind: 'BOTH', system: 1, person: 2, name: 'COUN 610, evidence from the system, 1 quote, and from a person, 2 quotes',
    });
    expect(row('UT-4').cells[col('COUN 655')]).toMatchObject({ kind: 'PERSON', name: 'COUN 655, evidence from a person, 1 quote' });
  });

  it('puts outdated evidence under the column of the course version that superseded it', () => {
    const cell = row('UT-7').cells[col('COUN 625')]!;
    expect(cell.kind).toBe('OUTDATED');
    expect(cell.outdated).toEqual([{ catalogYear: '2026-27', source: 'SYSTEM', quoteCount: 1 }]);
    expect(cell.name).toBe('COUN 625, outdated evidence from the system, 1 quote on the 2026-27 version');
    expect(row('UT-7').cells.filter((c) => c !== null)).toHaveLength(1);
  });

  it('keeps the current kind of a cell that also has outdated evidence, and names both', () => {
    const both = coverageView({ ...MATRIX, requirements: [requirement('X-1', {
      cells: [{ courseVersionId: 'v625', source: 'PERSON', quoteCount: 1 }],
      outdatedCells: [{ courseVersionId: 'v625-old', code: 'COUN 625', catalogYear: '2026-27', supersededBy: 'v625', source: 'SYSTEM', quoteCount: 2 }],
    })] });
    const cell = both.rows[0].cells[col('COUN 625')]!;
    expect(cell.kind).toBe('PERSON');
    expect(cell.name).toBe('COUN 625, evidence from a person, 1 quote, and outdated evidence from the system, 2 quotes on the 2026-27 version');
  });

  it('shows program-level documents in the program column', () => {
    const cell = row('UT-6').cells[col('Program')]!;
    expect(cell).toMatchObject({ kind: 'SYSTEM', name: 'Program documents, evidence from the system, accreditation-letter.pdf' });
    expect(cell.documents.map((d) => d.filename)).toEqual(['accreditation-letter.pdf']);
    expect(row('UT-6').cells.slice(0, -1).every((c) => c === null)).toBe(true);
  });

  it('gives rules-engine rows no cells and no flag', () => {
    for (const ref of ['UT-1', 'UT-8']) {
      expect(row(ref).rulesEngine).toBe(true);
      expect(row(ref).cells.every((c) => c === null)).toBe(true);
      expect(row(ref).flag).toBeNull();
    }
    expect(row('UT-2').rulesEngine).toBe(false);
  });
});

describe('flags', () => {
  const view = coverageView(MATRIX);

  it('counts each flag in chip order and leaves out flags no row carries', () => {
    expect(flagCounts(view.rows)).toEqual([
      { flag: 'ELECTIVE_ONLY', count: 1 }, { flag: 'SINGLE_COURSE', count: 1 }, { flag: 'OUTDATED_EVIDENCE', count: 1 }, { flag: 'NO_EVIDENCE', count: 1 },
    ]);
    expect(flagCounts(view.rows.filter((r) => r.flag !== 'NO_EVIDENCE')).map((c) => c.flag)).not.toContain('NO_EVIDENCE');
    expect(flagCounts([])).toEqual([]);
  });

  it('matches every row without a filter and only the flagged rows with one', () => {
    expect(view.rows.every((r) => matchesFlag(r, null))).toBe(true);
    expect(view.rows.filter((r) => matchesFlag(r, 'ELECTIVE_ONLY')).map((r) => r.requirement.ref)).toEqual(['UT-4']);
  });

  it('words each flag as where the evidence sits, in chip order', () => {
    expect(FLAG_ORDER.map((f) => FLAGS[f].label)).toEqual(['Rests on an elective', 'Graduation paths not fully known', 'Rests on one course', 'Outdated evidence', 'No evidence cited']);
  });

  it('rings a pill with the share of requirements that carry no flag', () => {
    expect(unflaggedShare(MATRIX)).toEqual({ value: 4, total: 8, label: '4 of 8 with no flag' });
  });
});

describe('keyboard movement', () => {
  const view = coverageView(MATRIX);
  // Rows with cells: 1 (UT-2: col 0), 2 (UT-3: cols 0, 1), 3 (UT-4: col 3), 5 (UT-6: col 4), 6 (UT-7: col 2).

  it('starts on the first cell in reading order', () => {
    expect(firstCell(view)).toEqual({ row: 1, col: 0 });
    expect(firstCell(coverageView({ ...MATRIX, requirements: [requirement('X-1', { type: 'NUMERIC' })] }))).toBeNull();
  });

  it('moves along a row over the cells that hold evidence and stays at its ends', () => {
    expect(move(view, { row: 2, col: 0 }, 'ArrowRight')).toEqual({ row: 2, col: 1 });
    expect(move(view, { row: 2, col: 1 }, 'ArrowRight')).toEqual({ row: 2, col: 1 });
    expect(move(view, { row: 2, col: 1 }, 'ArrowLeft')).toEqual({ row: 2, col: 0 });
    expect(move(view, { row: 2, col: 1 }, 'Home')).toEqual({ row: 2, col: 0 });
    expect(move(view, { row: 2, col: 0 }, 'End')).toEqual({ row: 2, col: 1 });
  });

  it('moves up and down to the nearest cell of the next row that has one, skipping empty rows', () => {
    expect(move(view, { row: 2, col: 1 }, 'ArrowDown')).toEqual({ row: 3, col: 3 });
    expect(move(view, { row: 3, col: 3 }, 'ArrowDown')).toEqual({ row: 5, col: 4 });
    expect(move(view, { row: 5, col: 4 }, 'ArrowUp')).toEqual({ row: 3, col: 3 });
    expect(move(view, { row: 3, col: 3 }, 'ArrowUp')).toEqual({ row: 2, col: 1 });
    expect(move(view, { row: 1, col: 0 }, 'ArrowUp')).toEqual({ row: 1, col: 0 });
    expect(move(view, { row: 6, col: 2 }, 'ArrowDown')).toEqual({ row: 6, col: 2 });
  });

  it('jumps to the first and last cell of the matrix', () => {
    expect(move(view, { row: 3, col: 3 }, 'FirstCell')).toEqual({ row: 1, col: 0 });
    expect(move(view, { row: 1, col: 0 }, 'LastCell')).toEqual({ row: 6, col: 2 });
  });
});
