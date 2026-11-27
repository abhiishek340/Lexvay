import { NUMERIC_TYPES } from '../../core/labels';
import { CoverageCourse, CoverageMatrix, CoverageProgramDocument, CoverageRequirement, CoverageRisk, CoverageSource } from '../../core/models';
import type { IconName } from '../../theme/icons';

/** The flags a row can carry; NONE carries no chip. */
export type Flag = Exclude<CoverageRisk, 'NONE'>;
export type FlagTone = 'ambiguous' | 'neutral' | 'missing';

/**
 * How each flag is shown, in the order the filter chips appear. The words describe where the evidence sits and never
 * judge it (D-392): no flag says that anything is sufficient or missing for a licence.
 */
export const FLAGS: Readonly<Record<Flag, { label: string; icon: IconName; tone: FlagTone }>> = {
  ELECTIVE_ONLY: { label: 'Rests on an elective', icon: 'phosphorWarningCircle', tone: 'ambiguous' },
  SINGLE_COURSE: { label: 'Rests on one course', icon: 'phosphorBook', tone: 'neutral' },
  OUTDATED_EVIDENCE: { label: 'Outdated evidence', icon: 'phosphorClock', tone: 'ambiguous' },
  NO_EVIDENCE: { label: 'No evidence cited', icon: 'phosphorCircleDashed', tone: 'missing' },
};
export const FLAG_ORDER = Object.keys(FLAGS) as Flag[];

/** A column: one current course version, or the program's own documents (degree plan, handbook, accreditation), last. */
export interface Column {
  readonly key: string;
  readonly index: number;
  readonly code: string;
  readonly title: string;
  /** Null for the program column. */
  readonly required: boolean | null;
}
export const PROGRAM_COLUMN = 'PROGRAM';

/** A run of adjacent columns that are all required or all electives: the marker under the codes. */
export interface ColumnGroup { readonly required: boolean; readonly span: number }

/** Evidence on a superseded version of the column's course, drawn as a dashed ring with a clock (D-394). */
export interface OutdatedPart { readonly catalogYear: string; readonly source: CoverageSource; readonly quoteCount: number }

/**
 * How a cell is drawn: a filled dot (the system's evidence), a ring (a person's), both as a dot with a ring, or a
 * dashed ring when the only evidence is on a superseded course version.
 */
export type CellKind = 'SYSTEM' | 'PERSON' | 'BOTH' | 'OUTDATED';

export interface Cell {
  readonly column: Column;
  readonly kind: CellKind;
  /** Distinct verified quotes per source on the current course version (always 0 in the program column). */
  readonly system: number;
  readonly person: number;
  readonly outdated: readonly OutdatedPart[];
  /** The program column's cited documents. */
  readonly documents: readonly CoverageProgramDocument[];
  /** "COUN 605, evidence from the system, 1 quote". */
  readonly name: string;
}

export interface Row {
  readonly requirement: CoverageRequirement;
  /** A number checked by the rules engine: no cells, ever (D-393). */
  readonly rulesEngine: boolean;
  /** One entry per column, null where nothing is cited. */
  readonly cells: readonly (Cell | null)[];
  readonly flag: Flag | null;
}

export interface CoverageView {
  readonly columns: readonly Column[];
  readonly groups: readonly ColumnGroup[];
  readonly rows: readonly Row[];
}

const quotes = (n: number): string => `${n} ${n === 1 ? 'quote' : 'quotes'}`;
const SOURCE_WORDS: Record<CoverageSource, string> = { SYSTEM: 'the system', PERSON: 'a person' };

/** "evidence from the system, 1 quote, and from a person, 2 quotes" */
function currentWords(system: number, person: number): string | null {
  const parts = [system > 0 ? `from the system, ${quotes(system)}` : null, person > 0 ? `from a person, ${quotes(person)}` : null].filter((p) => p !== null);
  return parts.length ? `evidence ${parts.join(', and ')}` : null;
}

function outdatedWords(parts: readonly OutdatedPart[]): string | null {
  return parts.length
    ? parts.map((p) => `outdated evidence from ${SOURCE_WORDS[p.source]}, ${quotes(p.quoteCount)} on the ${p.catalogYear} version`).join(', and ')
    : null;
}

function courseCell(column: Column, r: CoverageRequirement): Cell | null {
  const mine = r.cells.filter((c) => c.courseVersionId === column.key);
  const system = mine.filter((c) => c.source === 'SYSTEM').reduce((sum, c) => sum + c.quoteCount, 0);
  const person = mine.filter((c) => c.source === 'PERSON').reduce((sum, c) => sum + c.quoteCount, 0);
  const outdated = r.outdatedCells.filter((c) => c.supersededBy === column.key).map(({ catalogYear, source, quoteCount }) => ({ catalogYear, source, quoteCount }));
  if (mine.length === 0 && outdated.length === 0) { return null; }
  const kind: CellKind = system > 0 && person > 0 ? 'BOTH' : system > 0 ? 'SYSTEM' : person > 0 ? 'PERSON' : 'OUTDATED';
  const words = [currentWords(system, person), outdatedWords(outdated)].filter((w) => w !== null).join(', and ');
  return { column, kind, system, person, outdated, documents: [], name: `${column.code}, ${words}` };
}

function programCell(column: Column, r: CoverageRequirement): Cell | null {
  const documents = r.programLevel;
  if (documents.length === 0) { return null; }
  const system = documents.some((d) => d.source === 'SYSTEM');
  const person = documents.some((d) => d.source === 'PERSON');
  const kind: CellKind = system && person ? 'BOTH' : system ? 'SYSTEM' : 'PERSON';
  const from = [system ? 'the system' : null, person ? 'a person' : null].filter((p) => p !== null).join(' and from ');
  const files = [...new Set(documents.map((d) => d.filename))].join(', ');
  return { column, kind, system: 0, person: 0, outdated: [], documents, name: `Program documents, evidence from ${from}, ${files}` };
}

/** Adjacent columns with the same `required` value, in column order; the program column belongs to none. */
export function columnGroups(courses: readonly CoverageCourse[]): ColumnGroup[] {
  const groups: { required: boolean; span: number }[] = [];
  for (const course of courses) {
    const last = groups.at(-1);
    if (last && last.required === course.required) { last.span++; } else { groups.push({ required: course.required, span: 1 }); }
  }
  return groups;
}

/**
 * The matrix as the screen draws it: the courses in the order the API gives (required first, then by code, D-391)
 * and the program column last; one row per requirement in `ordinal` order, each cell looked up by column, outdated
 * evidence under the column of the course's current version.
 */
export function coverageView(matrix: CoverageMatrix): CoverageView {
  const columns: Column[] = [
    ...matrix.courses.map((c, index) => ({ key: c.courseVersionId, index, code: c.code, title: c.title, required: c.required })),
    { key: PROGRAM_COLUMN, index: matrix.courses.length, code: 'Program', title: 'Program documents', required: null },
  ];
  const rows = matrix.requirements.map((requirement): Row => {
    const rulesEngine = NUMERIC_TYPES.includes(requirement.type);
    const cells = rulesEngine ? columns.map(() => null) : columns.map((col) => (col.key === PROGRAM_COLUMN ? programCell(col, requirement) : courseCell(col, requirement)));
    return { requirement, rulesEngine, cells, flag: requirement.risk === 'NONE' ? null : requirement.risk };
  });
  return { columns, groups: columnGroups(matrix.courses), rows };
}

/** The number of rows carrying each flag, in chip order; a flag no row carries has no chip. */
export function flagCounts(rows: readonly Row[]): { flag: Flag; count: number }[] {
  return FLAG_ORDER.map((flag) => ({ flag, count: rows.filter((r) => r.flag === flag).length })).filter((c) => c.count > 0);
}

/** A row stays at full strength when no filter is chosen or it carries the chosen flag; the others dim. */
export function matchesFlag(row: Row, filter: Flag | null): boolean {
  return filter === null || row.flag === filter;
}

/** The pill ring: the share of requirements that carry no flag. */
export function unflaggedShare(matrix: CoverageMatrix): { value: number; total: number; label: string } {
  const total = matrix.requirements.length;
  const value = matrix.requirements.filter((r) => r.risk === 'NONE').length;
  return { value, total, label: `${value} of ${total} with no flag` };
}

export interface Position { readonly row: number; readonly col: number }
export type MoveKey = 'ArrowLeft' | 'ArrowRight' | 'ArrowUp' | 'ArrowDown' | 'Home' | 'End' | 'FirstCell' | 'LastCell';

function cellsIn(view: CoverageView, row: number): number[] {
  return view.rows[row]?.cells.flatMap((cell, col) => (cell ? [col] : [])) ?? [];
}

function nearest(cols: number[], col: number): number {
  return cols.reduce((best, c) => (Math.abs(c - col) < Math.abs(best - col) ? c : best));
}

/** The first cell of the matrix in reading order, the keyboard's entry point; null when nothing is cited anywhere. */
export function firstCell(view: CoverageView): Position | null {
  for (let row = 0; row < view.rows.length; row++) {
    const cols = cellsIn(view, row);
    if (cols.length) { return { row, col: cols[0] }; }
  }
  return null;
}

/**
 * Keyboard movement between the cells that hold evidence (empty cells are skipped): left and right along the row,
 * up and down to the nearest cell of the next row that has one, Home and End to the ends of the row, Ctrl+Home and
 * Ctrl+End (FirstCell, LastCell) to the ends of the matrix. A move with nowhere to go stays put.
 */
export function move(view: CoverageView, from: Position, key: MoveKey): Position {
  const here = cellsIn(view, from.row);
  switch (key) {
    case 'ArrowRight': { const col = here.find((c) => c > from.col); return col === undefined ? from : { row: from.row, col }; }
    case 'ArrowLeft': { const col = here.filter((c) => c < from.col).at(-1); return col === undefined ? from : { row: from.row, col }; }
    case 'Home': return here.length ? { row: from.row, col: here[0] } : from;
    case 'End': return here.length ? { row: from.row, col: here[here.length - 1] } : from;
    case 'FirstCell': return firstCell(view) ?? from;
    case 'LastCell': {
      for (let row = view.rows.length - 1; row >= 0; row--) {
        const col = cellsIn(view, row).at(-1);
        if (col !== undefined) { return { row, col }; }
      }
      return from;
    }
    case 'ArrowDown':
    case 'ArrowUp': {
      const step = key === 'ArrowDown' ? 1 : -1;
      for (let row = from.row + step; row >= 0 && row < view.rows.length; row += step) {
        const cols = cellsIn(view, row);
        if (cols.length) { return { row, col: nearest(cols, from.col) }; }
      }
      return from;
    }
  }
}
