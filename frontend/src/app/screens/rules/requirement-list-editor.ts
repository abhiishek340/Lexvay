import { computed, signal } from '@angular/core';
import { NUMERIC_TYPES } from '../../core/labels';
import { NumericField, NumericOperator, RequirementDraft, RequirementType, RequirementView } from '../../core/models';

/** One row of the list while it is edited. The key is the client's own and survives moves and edits. */
export interface EditableRequirement {
  key: number;
  ref: string;
  title: string;
  text: string;
  type: RequirementType | null;
  numericField: NumericField | null;
  numericOperator: NumericOperator | null;
  numericValue: number | null;
  /** Carried through unchanged until the screen has a cell for them; a new row has none. */
  searchTerms: string[];
}

export type RequirementPatch = Partial<Omit<EditableRequirement, 'key'>>;

/** The server's validation messages for a rejected list: per row (by key) where a message names one, otherwise general. */
export interface ListProblems { rows: ReadonlyMap<number, readonly string[]>; general: readonly string[] }

const NO_PROBLEMS: ListProblems = { rows: new Map(), general: [] };

/**
 * The requirement list as a reviewer corrects it before approval: every row editable at once, rows added,
 * moved and deleted, the whole list sent back in the API's body shape. Plain signals, no Angular dependencies.
 */
export class RequirementListEditor {
  private readonly original = signal<readonly RequirementView[]>([]);
  private nextKey = 1;

  readonly rows = signal<readonly EditableRequirement[]>([]);
  readonly problems = signal<ListProblems>(NO_PROBLEMS);
  /** True once the list differs from what the server last returned, in content or in order. */
  readonly dirty = computed(() => {
    const rows = this.rows();
    const original = this.original();
    return rows.length !== original.length || rows.some((row, i) => !sameContent(row, original[i]));
  });

  /** The server's list, as loaded or as returned by a save. */
  load(requirements: readonly RequirementView[]): void {
    this.original.set(requirements);
    this.restore();
  }

  /** Discard: back to the server's list. */
  restore(): void {
    this.rows.set(this.original().map((r) => ({
      key: this.nextKey++, ref: r.ref, title: r.title, text: r.text, type: r.type,
      numericField: r.numericField, numericOperator: r.numericOperator, numericValue: r.numericValue,
      searchTerms: [...r.searchTerms],
    })));
    this.problems.set(NO_PROBLEMS);
  }

  /** Appends an empty row and returns it, so the screen can focus its ref. */
  add(): EditableRequirement {
    const row: EditableRequirement = { key: this.nextKey++, ref: '', title: '', text: '', type: null, numericField: null, numericOperator: null, numericValue: null, searchTerms: [] };
    this.rows.update((rows) => [...rows, row]);
    return row;
  }

  update(key: number, patch: RequirementPatch): void {
    this.rows.update((rows) => rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }

  /** Moves a row one place up (-1) or down (1); stays put at either end. */
  move(key: number, delta: -1 | 1): void {
    const rows = [...this.rows()];
    const from = rows.findIndex((r) => r.key === key);
    const to = from + delta;
    if (from === -1 || to < 0 || to >= rows.length) { return; }
    [rows[from], rows[to]] = [rows[to], rows[from]];
    this.rows.set(rows);
  }

  remove(key: number): void {
    this.rows.update((rows) => rows.filter((r) => r.key !== key));
    this.problems.update(({ rows, general }) => ({ rows: new Map([...rows].filter(([k]) => k !== key)), general }));
  }

  /**
   * The PUT body's requirements, in list order. Numeric fields travel only with a numeric type; the screen keeps
   * them on the row so switching the type back does not lose them.
   */
  toDrafts(): RequirementDraft[] {
    return this.rows().map((r) => {
      const numeric = r.type !== null && NUMERIC_TYPES.includes(r.type);
      return {
        ref: r.ref, title: r.title, text: r.text, type: r.type,
        numeric_field: numeric ? r.numericField : null,
        numeric_operator: numeric ? r.numericOperator : null,
        numeric_value: numeric ? r.numericValue : null,
        search_terms: r.searchTerms,
      };
    });
  }

  /** Records the server's rejection against the rows that were sent (see requirementProblems). */
  reject(detail: string): void {
    this.problems.set(requirementProblems(detail, this.rows()));
  }

  clearProblems(): void {
    this.problems.set(NO_PROBLEMS);
  }
}

function sameContent(row: EditableRequirement, r: RequirementView | undefined): boolean {
  return !!r && row.ref === r.ref && row.title === r.title && row.text === r.text && row.type === r.type
    && row.numericField === r.numericField && row.numericOperator === r.numericOperator && row.numericValue === r.numericValue;
}

const LIST_PREFIX = 'Requirement list is invalid: ';
const ROW = /^requirements\[(\d+)\]\.?\s*/;
const QUOTED_REF = /'([^']+)'/;

/**
 * Maps the server's 400 detail for a rejected list onto the rows that were sent. The parser joins its messages
 * with "; " and names the row as requirements[i] (i is the position in the body, which is the list order); a
 * message that only names a ref in quotes goes to the row with that ref. Anything else is general.
 */
export function requirementProblems(detail: string, rows: readonly EditableRequirement[]): ListProblems {
  const messages = detail.startsWith(LIST_PREFIX) ? detail.slice(LIST_PREFIX.length).split('; ') : [detail];
  const byRow = new Map<number, string[]>();
  const general: string[] = [];
  for (const message of messages) {
    const index = ROW.exec(message);
    const refMatch = index ? null : QUOTED_REF.exec(message);
    const row = index ? rows[Number(index[1])] : rows.find((r) => refMatch && r.ref.trim() === refMatch[1]);
    if (row) {
      byRow.set(row.key, [...(byRow.get(row.key) ?? []), sentence(index ? message.slice(index[0].length) : message)]);
    } else {
      general.push(sentence(message));
    }
  }
  return { rows: byRow, general };
}

/** "numeric_value must be a finite positive number" becomes "Numeric value must be a finite positive number." */
function sentence(message: string): string {
  const words = message.replace(/_/g, ' ').trim();
  const capitalised = words.charAt(0).toUpperCase() + words.slice(1);
  return /[.!?]$/.test(capitalised) ? capitalised : `${capitalised}.`;
}
