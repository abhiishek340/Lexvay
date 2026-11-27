import { describe, expect, it } from 'vitest';
import { RequirementView } from '../../core/models';
import { RequirementListEditor, requirementProblems } from './requirement-list-editor';

function requirement(ref: string, patch: Partial<RequirementView> = {}): RequirementView {
  return { id: `id-${ref}`, ref, title: `Title ${ref}`, text: `Text ${ref}`, type: 'SUBJECT', numericField: null, numericOperator: null, numericValue: null, searchTerms: [], ...patch };
}
const hours = requirement('CO-5', { type: 'SUPERVISED_HOURS', numericField: 'INTERNSHIP_HOURS', numericOperator: '>=', numericValue: 600 });

function editor(): RequirementListEditor {
  const e = new RequirementListEditor();
  e.load([requirement('CO-1'), requirement('CO-2'), hours]);
  return e;
}

describe('RequirementListEditor', () => {
  it('starts clean with the server list and sends it back unchanged in the API body shape', () => {
    const e = editor();
    expect(e.dirty()).toBe(false);
    expect(e.toDrafts()).toEqual([
      { ref: 'CO-1', title: 'Title CO-1', text: 'Text CO-1', type: 'SUBJECT', numeric_field: null, numeric_operator: null, numeric_value: null, search_terms: [] },
      { ref: 'CO-2', title: 'Title CO-2', text: 'Text CO-2', type: 'SUBJECT', numeric_field: null, numeric_operator: null, numeric_value: null, search_terms: [] },
      { ref: 'CO-5', title: 'Title CO-5', text: 'Text CO-5', type: 'SUPERVISED_HOURS', numeric_field: 'INTERNSHIP_HOURS', numeric_operator: '>=', numeric_value: 600, search_terms: [] },
    ]);
  });

  it('becomes dirty on an edit and clean again when the edit is undone by hand', () => {
    const e = editor();
    const key = e.rows()[0].key;
    e.update(key, { title: 'Credit hours' });
    expect(e.dirty()).toBe(true);
    expect(e.toDrafts()[0].title).toBe('Credit hours');
    e.update(key, { title: 'Title CO-1' });
    expect(e.dirty()).toBe(false);
  });

  it('edits the search terms like any other cell: dirty on a change, clean when undone, sent in the body', () => {
    const e = new RequirementListEditor();
    e.load([requirement('CO-1', { searchTerms: ['code of ethics'] })]);
    const key = e.rows()[0].key;
    e.update(key, { searchTerms: ['code of ethics', 'confidentiality'] });
    expect(e.dirty()).toBe(true);
    expect(e.toDrafts()[0].search_terms).toEqual(['code of ethics', 'confidentiality']);
    e.update(key, { searchTerms: ['confidentiality', 'code of ethics'] });
    expect(e.dirty()).toBe(true);
    e.update(key, { searchTerms: ['code of ethics'] });
    expect(e.dirty()).toBe(false);
    e.update(key, { searchTerms: ['changed'] });
    e.restore();
    expect(e.rows()[0].searchTerms).toEqual(['code of ethics']);
  });

  it('sends back the search terms each row was loaded with after an edit, a move and a type change', () => {
    const e = new RequirementListEditor();
    e.load([requirement('CO-1', { searchTerms: ['code of ethics', 'confidentiality'] }), requirement('CO-2', { searchTerms: ['fieldwork'] }), hours]);
    const [first, second] = e.rows().map((r) => r.key);
    e.update(first, { title: 'Ethics coursework' });
    e.move(second, -1);
    e.update(second, { type: 'ACCREDITATION' });
    expect(e.toDrafts().map((d) => [d.ref, d.title, d.search_terms])).toEqual([
      ['CO-2', 'Title CO-2', ['fieldwork']],
      ['CO-1', 'Ethics coursework', ['code of ethics', 'confidentiality']],
      ['CO-5', 'Title CO-5', []],
    ]);
  });

  it('does not share the loaded terms with the row: a discard returns to the server list', () => {
    const e = new RequirementListEditor();
    e.load([requirement('CO-1', { searchTerms: ['aging'] })]);
    e.update(e.rows()[0].key, { title: 'Changed' });
    e.restore();
    expect(e.dirty()).toBe(false);
    expect(e.toDrafts()[0].search_terms).toEqual(['aging']);
  });

  it('adds an empty row at the end with no type chosen', () => {
    const e = editor();
    const row = e.add();
    expect(e.rows().at(-1)).toEqual(row);
    expect(row).toMatchObject({ ref: '', title: '', text: '', type: null });
    expect(e.dirty()).toBe(true);
    expect(e.toDrafts().at(-1)?.type).toBeNull();
    expect(e.toDrafts().at(-1)?.search_terms).toEqual([]);
  });

  it('moves rows up and down, stays put at either end, and a move alone makes the list dirty', () => {
    const e = editor();
    const [first, second, third] = e.rows().map((r) => r.key);
    e.move(first, -1);
    e.move(third, 1);
    expect(e.dirty()).toBe(false);
    e.move(second, -1);
    expect(e.rows().map((r) => r.ref)).toEqual(['CO-2', 'CO-1', 'CO-5']);
    expect(e.dirty()).toBe(true);
    e.move(second, 1);
    expect(e.dirty()).toBe(false);
  });

  it('deletes a row', () => {
    const e = editor();
    e.remove(e.rows()[1].key);
    expect(e.rows().map((r) => r.ref)).toEqual(['CO-1', 'CO-5']);
    expect(e.dirty()).toBe(true);
  });

  it('sends numeric fields only with a numeric type, and keeps them on the row when the type changes back', () => {
    const e = editor();
    const key = e.rows()[2].key;
    e.update(key, { type: 'SUBJECT' });
    expect(e.toDrafts()[2]).toMatchObject({ type: 'SUBJECT', numeric_field: null, numeric_operator: null, numeric_value: null });
    e.update(key, { type: 'SUPERVISED_HOURS' });
    expect(e.toDrafts()[2]).toMatchObject({ numeric_field: 'INTERNSHIP_HOURS', numeric_operator: '>=', numeric_value: 600 });
    expect(e.dirty()).toBe(false);
  });

  it('restores the server list and forgets the problems of the rejected save', () => {
    const e = editor();
    e.update(e.rows()[0].key, { ref: '' });
    e.add();
    e.reject('Requirement list is invalid: requirements[0].ref must not be blank');
    expect(e.problems().rows.size).toBe(1);
    e.restore();
    expect(e.dirty()).toBe(false);
    expect(e.rows().map((r) => r.ref)).toEqual(['CO-1', 'CO-2', 'CO-5']);
    expect(e.problems().rows.size).toBe(0);
  });

  it('a new server list (after a save) becomes the clean state', () => {
    const e = editor();
    e.update(e.rows()[0].key, { title: 'Credit hours' });
    e.load([requirement('CO-1', { title: 'Credit hours' }), requirement('CO-2'), hours]);
    expect(e.dirty()).toBe(false);
  });

  it('keeps a row problem on that row when the row moves, and drops it when the row is deleted', () => {
    const e = editor();
    e.reject('Requirement list is invalid: requirements[1].title must not be blank');
    const key = e.rows()[1].key;
    e.move(key, -1);
    expect(e.problems().rows.get(key)).toEqual(['Title must not be blank.']);
    e.remove(key);
    expect(e.problems().rows.size).toBe(0);
  });
});

describe('requirementProblems', () => {
  const rows = editor().rows();

  it('puts each message under the row it names by position', () => {
    const p = requirementProblems(
      'Requirement list is invalid: requirements[0].ref must not be blank; requirements[2] has type SUPERVISED_HOURS and needs numeric_field, '
        + 'numeric_operator and numeric_value; requirements[2].title must be at most 200 characters',
      rows,
    );
    expect(p.general).toEqual([]);
    expect(p.rows.get(rows[0].key)).toEqual(['Ref must not be blank.']);
    expect(p.rows.get(rows[2].key)).toEqual([
      'Has type SUPERVISED HOURS and needs numeric field, numeric operator and numeric value.',
      'Title must be at most 200 characters.',
    ]);
  });

  it('a message naming only a ref goes to the row with that ref', () => {
    const p = requirementProblems("Requirement list is invalid: ref 'CO-2' is not allowed here", rows);
    expect(p.rows.get(rows[1].key)).toEqual(["Ref 'CO-2' is not allowed here."]);
  });

  it('messages that name no row, or a row that does not exist, are general', () => {
    expect(requirementProblems('Requirement list is invalid: requirements must contain at least one entry', rows))
      .toEqual({ rows: new Map(), general: ['Requirements must contain at least one entry.'] });
    expect(requirementProblems('Requirement list is invalid: requirements[7].ref must not be blank', rows).general).toEqual(['Requirements[7].ref must not be blank.']);
    expect(requirementProblems('The request contains a value that cannot be stored', rows).general).toEqual(['The request contains a value that cannot be stored.']);
  });
});
