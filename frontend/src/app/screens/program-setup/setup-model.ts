import { NUMERIC_FIELD_LABEL, NUMERIC_TYPES } from '../../core/labels';
import { Course, CourseVersion, DocumentView, NumericField, Program, ProgramDocument, ProgramFact, RequirementView, RuleVersionView } from '../../core/models';

/** The version a course is taught under now: the one no other version of the course supersedes. */
export function currentVersion(course: Course): CourseVersion | null {
  return course.versions.find((v) => !course.versions.some((other) => other.supersedesId === v.id)) ?? null;
}

/** The license types the tenant's rule versions use, for the Details step's choice; the person may also type another. */
export function licenseTypes(versions: readonly RuleVersionView[]): string[] {
  return [...new Set(versions.map((v) => v.licenseType))].sort();
}

/**
 * The rule version an assessment started on `today` uses for a state and license type: approved, in force or final
 * and already effective, not ended, the latest effective date first, then the newest entry. The same choice as the
 * server's RuleVersionRepository.findInForce, so readiness says exactly what the run will find.
 */
export function assessedVersion(all: readonly RuleVersionView[], jurisdiction: string, licenseType: string, today: string): RuleVersionView | null {
  return all
    .filter((v) => v.approval && v.jurisdiction === jurisdiction && v.licenseType === licenseType
      && (v.legalStatus === 'IN_FORCE' || v.legalStatus === 'FINAL_NOT_EFFECTIVE')
      && v.effectiveFrom !== null && v.effectiveFrom <= today && (v.effectiveTo === null || v.effectiveTo > today))
    .sort((a, b) => b.effectiveFrom!.localeCompare(a.effectiveFrom!) || b.createdAt.localeCompare(a.createdAt))[0] ?? null;
}

/** The versions an assessment of the program would use today, one per target state that has one. */
export function assessedVersions(program: Program, all: readonly RuleVersionView[], today: string): RuleVersionView[] {
  return program.jurisdictions.map((code) => assessedVersion(all, code, program.licenseType, today)).filter((v): v is RuleVersionView => v !== null);
}

/** The numbers the rules engine checks for these requirement lists, in the order the product names them. */
export function numericFieldsUsed(lists: readonly (readonly RequirementView[])[]): NumericField[] {
  const used = new Set(lists.flatMap((list) => list.filter((r) => NUMERIC_TYPES.includes(r.type) && r.numericField).map((r) => r.numericField!)));
  return (Object.keys(NUMERIC_FIELD_LABEL) as NumericField[]).filter((field) => used.has(field));
}

/** Where a document's text stands: read (it has pages), still being read, or not readable (the job failed). */
export type DocumentState = 'READING' | 'READY' | 'FAILED';
export type Ingest = DocumentView['ingest'];

/**
 * A document with pages is ready. Without pages its ingest job decides: failed is FAILED, anything else (queued,
 * running, or not looked up yet) is READING; a job that finished without a page left nothing to quote, which is FAILED.
 */
export function documentState(document: ProgramDocument, ingest: Ingest | undefined): DocumentState {
  if (document.pageCount > 0) { return 'READY'; }
  if (ingest?.status === 'FAILED' || ingest?.status === 'DONE') { return 'FAILED'; }
  return 'READING';
}

export type CheckId = 'RULES' | 'COURSE_DOCUMENTS' | 'READING' | 'FACTS';
/** One line of the checklist. `blocking` lines disable Run assessment while not ok; a warning never does. */
export interface ReadinessCheck {
  readonly id: CheckId;
  readonly ok: boolean;
  readonly blocking: boolean;
  readonly text: string;
  /** What the line is about: the state codes without rules, the courses without a document, the facts missing. */
  readonly items: readonly string[];
}
export interface Readiness { readonly checks: readonly ReadinessCheck[]; readonly ready: boolean; readonly fieldsUsed: readonly NumericField[] }

export interface SetupDocument { readonly document: ProgramDocument; readonly state: DocumentState }

const list = (items: readonly string[]): string => items.join(', ');

/**
 * The readiness checklist: every target state has an approved rule version in effect for the license type; every
 * course's current version has a ready document; no document is still being read; every number those rules check is
 * recorded as a program fact. The first three block Run assessment. The last is a warning: without the fact the rules
 * engine reports the requirement as missing evidence, which is correct, and the line says so. A program without courses
 * gets a warning too: there is nothing to cover, and every requirement matched against documents will find nothing.
 * `requirements` holds the requirement list of each assessed version by version id.
 */
export function programReadiness(
  program: Program, courses: readonly Course[], documents: readonly SetupDocument[], facts: readonly ProgramFact[],
  ruleVersions: readonly RuleVersionView[], requirements: ReadonlyMap<string, readonly RequirementView[]>, today: string,
): Readiness {
  const withoutRules = program.jurisdictions.filter((code) => !assessedVersion(ruleVersions, code, program.licenseType, today));
  const readyVersions = new Set(documents.filter((d) => d.state === 'READY').map((d) => d.document.courseVersionId));
  const uncovered = courses.filter((c) => !readyVersions.has(currentVersion(c)?.id ?? null)).map((c) => c.code);
  const reading = documents.filter((d) => d.state === 'READING').length;
  const fieldsUsed = numericFieldsUsed(assessedVersions(program, ruleVersions, today).map((v) => requirements.get(v.id) ?? []));
  const recorded = new Set(facts.map((f) => f.key));
  const missing = fieldsUsed.filter((field) => !recorded.has(field)).map((field) => NUMERIC_FIELD_LABEL[field]);
  const checks: ReadinessCheck[] = [
    {
      id: 'RULES', blocking: true, ok: withoutRules.length === 0, items: withoutRules,
      text: withoutRules.length === 0 ? 'Every state has approved rules' : `No approved rules for ${list(withoutRules)}`,
    },
    {
      // Without courses there is nothing to cover, which is worth a warning but does not stop a run.
      id: 'COURSE_DOCUMENTS', blocking: courses.length > 0, ok: courses.length > 0 && uncovered.length === 0, items: uncovered,
      text: courses.length === 0 ? 'No courses yet' : uncovered.length === 0 ? 'Every course has a ready document' : `No ready document for ${list(uncovered)}`,
    },
    {
      id: 'READING', blocking: true, ok: reading === 0, items: [],
      text: reading === 0 ? 'No document is still being read' : `${reading} ${reading === 1 ? 'document is' : 'documents are'} still being read`,
    },
    {
      id: 'FACTS', blocking: false, ok: missing.length === 0, items: missing,
      text: fieldsUsed.length === 0 ? 'No numbers to record' : missing.length === 0 ? 'Every number the rules check is recorded'
        : `${list(missing)} not recorded: the rules engine will report ${missing.length === 1 ? 'it' : 'them'} as missing evidence`,
    },
  ];
  return { checks, ready: checks.every((c) => c.ok || !c.blocking), fieldsUsed };
}

/**
 * Whether a selected passage shows the number as written. A warning only (the server verifies the quote, not the
 * number): the digits of the value must appear in the passage, with or without thousands separators.
 */
export function quoteShowsValue(value: number | null, quote: string): boolean {
  if (value === null) { return true; }
  const written = String(value);
  const numbers = (quote.match(/\d[\d,]*(?:\.\d+)?/g) ?? []).map((n) => n.replace(/,/g, ''));
  return numbers.some((n) => n === written || Number(n) === value);
}

/** The newest fact per numeric field and the earlier ones, newest first (the server lists them newest first). */
export function factHistory(facts: readonly ProgramFact[], field: NumericField): { current: ProgramFact | null; earlier: ProgramFact[] } {
  const forField = facts.filter((f) => f.key === field);
  return { current: forField.find((f) => f.current) ?? null, earlier: forField.filter((f) => !f.current) };
}

/** The first step that still needs work, so opening setup lands where the person left off. */
export function firstOpenStep(courses: readonly Course[], documents: readonly SetupDocument[], readiness: Readiness): SetupStep {
  if (courses.length === 0) { return 'COURSES'; }
  if (documents.length === 0 || readiness.checks.find((c) => c.id === 'COURSE_DOCUMENTS')?.ok === false) { return 'DOCUMENTS'; }
  if (readiness.checks.find((c) => c.id === 'FACTS')?.ok === false) { return 'FACTS'; }
  return 'READINESS';
}

export type SetupStep = 'DETAILS' | 'STATES' | 'COURSES' | 'DOCUMENTS' | 'FACTS' | 'READINESS';
/** The Stepper's values, in order. */
export const SETUP_STEPS: readonly SetupStep[] = ['DETAILS', 'STATES', 'COURSES', 'DOCUMENTS', 'FACTS', 'READINESS'];
