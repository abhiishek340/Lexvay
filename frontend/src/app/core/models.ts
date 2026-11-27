export type Role = 'CONTRIBUTOR' | 'REVIEWER' | 'SIGNER' | 'ADMIN';
export type Finding = 'EVIDENCE_FOUND' | 'EVIDENCE_MISSING' | 'AMBIGUOUS';
export type DecisionKind = 'ACCEPTED' | 'EVIDENCE_REQUESTED' | 'GAP_CONFIRMED';
export type Outcome = 'MEETS' | 'DOES_NOT_MEET' | 'NOT_DETERMINED';
/** Where a page's stored text came from (F11); OCR pages carry a confidence from 0 to 100. */
export type TextSource = 'TEXT_LAYER' | 'OCR';

export interface UserView { id: string; tenantId: string; email: string; displayName: string; role: Role; }
/** GET /users: the people of the caller's tenant, for resolving ids to names. */
export interface UserSummary { id: string; displayName: string; role: Role; email: string; }
/** GET /users as an administrator sees it (F5a): the account's state as well. */
export interface Account extends UserSummary { active: boolean; createdAt: string; passwordSet: boolean; }
/** POST /users and POST /users/{id}/setup-code: the only copy of the code, valid until expiresAt (D-372). */
export interface UserSetup { user: Account; setupCode: string; expiresAt: string; }
export interface NewUser { email: string; displayName: string; role: Role; }
export interface SessionView { authenticated: boolean; user: UserView | null; csrfHeaderName: string | null; tenantName: string | null; demo: boolean; }

export interface Program { id: string; name: string; licenseType: string; catalogYear: string; jurisdictions: string[]; createdAt: string; }
/** POST /programs. Name, license type, catalog year and target states are fixed once the program exists. */
export interface NewProgram { name: string; licenseType: string; catalogYear: string; jurisdictions: string[]; }
/** One catalog-year version of a course; the current one is the version no other version supersedes. */
export interface CourseVersion { id: string; courseId: string; programId: string; catalogYear: string; required: boolean; supersedesId: string | null; }
/** GET /programs/{id}/courses: a course with its versions, oldest first. */
export interface Course { id: string; programId: string; code: string; title: string; versions: CourseVersion[]; }
/** POST /programs/{id}/courses creates the course and its first version for the program's catalog year (D-039). */
export interface NewCourse { code: string; title: string; required: boolean; }
export interface NewCourseVersion { catalogYear: string; required: boolean; supersedesId: string | null; }
/** GET /programs/{id}/facts: every recorded fact, newest first; `current` marks the newest per key (D-036). */
export interface ProgramFact {
  id: string; programId: string; key: NumericField; value: number; evidenceSpanId: string; documentId: string; filename: string;
  pageNumber: number; quote: string; createdAt: string; createdBy: string; current: boolean;
}
/** POST /programs/{id}/facts: the server verifies the quote against the stored page (product rule 2). */
export interface NewProgramFact { key: NumericField; value: number; documentId: string; pageNumber: number; quote: string; }
export type DocumentKind = 'SYLLABUS' | 'DEGREE_PLAN' | 'HANDBOOK' | 'ACCREDITATION';
/** POST /programs/{id}/documents (202): the document is stored and read by an ingest job. */
export interface UploadResult { documentId: string; jobId: string; }
/** GET /documents/{id}: the document with its pages and the latest ingest job (null for a document stored with its pages). */
export interface DocumentView {
  id: string; programId: string | null; courseVersionId: string | null; filename: string; kind: string; createdAt: string;
  pages: { pageNumber: number }[]; ingest: { jobId: string; status: JobStatus; error: string | null } | null;
}
export interface Jurisdiction { id: string; code: string; name: string; }

export interface JurisdictionSummary { jurisdiction: string; total: number; evidenceFound: number; evidenceMissing: number; ambiguous: number; decided: number; }
export interface AssessmentView { id: string; programId: string; status: 'QUEUED' | 'RUNNING' | 'FINISHED' | 'FAILED'; startedAt: string | null; finishedAt: string | null; llmModel: string; promptVersion: string; jurisdictions: JurisdictionSummary[]; createdAt: string; }

export type RequirementType = 'NUMERIC' | 'SUBJECT' | 'SUPERVISED_HOURS' | 'ACCREDITATION' | 'JURISDICTION_SPECIFIC';
export type NumericField = 'TOTAL_CREDITS' | 'PRACTICUM_HOURS' | 'INTERNSHIP_HOURS';
export type NumericOperator = '>=' | '<=' | '=' | '>' | '<';
export interface RequirementView { id: string; ref: string; title: string; text: string; type: RequirementType; numericField: NumericField | null; numericOperator: NumericOperator | null; numericValue: number | null; searchTerms: string[]; }
export interface Evidence { spanId: string; documentId: string; filename: string; kind: string; courseVersionId: string | null; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; charStart: number; charEnd: number; quote: string; textSource: TextSource; ocrConfidence: number | null; }
export interface Decision { kind: DecisionKind; note: string | null; userId: string; userDisplayName: string | null; at: string; }
export interface Mapping { id: string; assessmentId: string; jurisdictionId: string; jurisdiction: string; ruleVersionId: string; requirement: RequirementView; finding: Finding; method: 'RULES_ENGINE' | 'MATCH'; rationale: string; stale: boolean; evidence: Evidence | null; decision: Decision | null;
  /** How many times a person answered an evidence request on this requirement (F4); stays after a decision. */
  responseCount: number; }
export interface MappingsView { assessmentId: string; jurisdiction: string | null; mappings: Mapping[]; }

export interface Determination { id: string; programId: string; jurisdiction: string; ruleVersionId: string; ruleVersionLabel: string; assessmentId: string; outcome: Outcome; signedBy: string; signedByName: string; signedAt: string; status: 'SIGNED' | 'REOPENED'; versionsHash: string; courseVersionIds: string[]; }
export type DeterminationStatus = Determination['status'];

/** GET /determinations/register: one signed determination exactly as signed (F8). */
export interface RegisterRow {
  determinationId: string; programId: string; programName: string; licenseType: string; catalogYear: string;
  jurisdictionCode: string; jurisdictionName: string; outcome: Outcome; status: DeterminationStatus;
  signedBy: string; signedByName: string; signedAt: string; ruleVersionId: string; ruleVersionLabel: string;
  ruleEffectiveFrom: string; versionsHash: string; assessmentId: string; courseVersionCount: number;
}
export interface RegisterFilter { programId: string | null; jurisdiction: string | null; outcome: Outcome | null; includeReopened: boolean; }

export type DisclosureReason = 'SIGNED_NO_DETERMINATION' | 'NO_CURRENT_SIGNATURE';
/** A target jurisdiction in one of the three lists; the determination fields are null without a current signature. */
export interface DisclosureEntry {
  jurisdictionCode: string; jurisdictionName: string; determinationId: string | null; signedBy: string | null;
  signedByName: string | null; signedAt: string | null; ruleVersionLabel: string | null; versionsHash: string | null;
  reason: DisclosureReason | null; reopenedDeterminationId: string | null; reopenedAt: string | null;
}
/** GET /programs/{id}/determination-lists: every target jurisdiction in exactly one list. */
export interface DeterminationLists {
  program: { id: string; name: string; licenseType: string; catalogYear: string };
  generatedAt: string;
  determinedToMeet: DisclosureEntry[];
  determinedNotToMeet: DisclosureEntry[];
  noDeterminationMade: DisclosureEntry[];
}

export interface ChangeEvent {
  id: string; kind: 'RULE_VERSION' | 'COURSE_VERSION'; oldId: string; newId: string; status: 'OPEN' | 'HANDLED'; createdAt: string;
  jurisdiction: string | null; oldLabel: string | null; newLabel: string | null; newApproved: boolean;
  courseCode: string | null; oldCatalogYear: string | null; newCatalogYear: string | null; newVersionHasDocument: boolean;
  reopenedDeterminationIds: string[];
}
export interface ReportStatusView { id: string; determinationId: string; status: 'QUEUED' | 'READY' | 'FAILED'; }
/** The report a checked file is exactly, and its determination as it stands now (POST /reports/check, F14). */
export interface ReportCheckMatch {
  reportId: string; generatedAt: string; determinationId: string; programId: string; programName: string; jurisdictionCode: string;
  ruleVersionLabel: string; outcome: Outcome; signedByName: string; signedAt: string; determinationStatus: 'SIGNED' | 'REOPENED';
  reopenedAt: string | null; versionsHash: string;
}
export interface ReportCheck { match: boolean; sha256: string; matchDetails: ReportCheckMatch | null; }
export interface AuditEntry { id: string; userId: string | null; action: string; entityType: string; entityId: string | null; detail: Record<string, unknown>; createdAt: string; }
export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
export interface RuleVersionView {
  id: string; jurisdiction: string; licenseType: string; label: string; sourceUrl: string; effectiveFrom: string;
  supersedesId: string | null; documentId: string; approval: { by: string; at: string } | null; requirementCount: number;
  extraction: { jobId: string; status: JobStatus; error: string | null } | null; createdAt: string;
}
/** POST /rule-versions; the server stores the text as the version's document and queues extraction. */
export interface NewRuleVersion { jurisdiction: string; licenseType: string; label: string; sourceUrl: string; effectiveFrom: string; supersedesId: string | null; text: string; }
export interface RequirementsView { ruleVersionId: string; approved: boolean; requirements: RequirementView[]; }
/**
 * One requirement in the PUT /rule-versions/{id}/requirements body (the model output schema, snake case). The
 * type is null while a reviewer has not chosen one; the server rejects that row with a message naming it.
 */
export interface RequirementDraft {
  ref: string; title: string; text: string; type: RequirementType | null;
  numeric_field: NumericField | null; numeric_operator: NumericOperator | null; numeric_value: number | null;
  /** The vocabulary a syllabus would use for the requirement (F7c), at most 12 terms; absent means none. */
  search_terms: string[];
}

export type DiffOp = 'EQUAL' | 'DELETE' | 'INSERT';
export interface DiffSegment { op: DiffOp; text: string; }
export type DiffStatus = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
/** SEARCH_TERMS: the terms changed; it never makes a requirement a different one for evaluation (D-254). */
export type DiffAspect = 'TITLE' | 'TEXT' | 'TYPE' | 'NUMERIC' | 'SEARCH_TERMS';
export interface DiffRow { status: DiffStatus; refChanged: boolean; before: RequirementView | null; after: RequirementView | null; changes: DiffAspect[]; textDiff: DiffSegment[] | null; }
export interface RuleVersionDiff {
  from: { id: string; label: string; effectiveFrom: string }; to: { id: string; label: string; effectiveFrom: string };
  summary: { added: number; removed: number; changed: number; unchanged: number }; requirements: DiffRow[]; ruleTextDiff: DiffSegment[];
}
export interface ProgramOverview { program: Program; assessment: AssessmentView | null; determinations: Determination[]; openChangeEvents: ChangeEvent[]; undecidedTotal: number; }
/** GET /documents/{id}/pages/{n}: one immutable page of extracted text. */
export interface DocumentPage { documentId: string; filename: string; kind: string; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; pageCount: number; text: string; textSource: TextSource; ocrConfidence: number | null; }

/** GET /programs/{id}/documents: the program's documents with their course context; `current` is false for an earlier catalog year. */
export interface ProgramDocument {
  id: string; filename: string; kind: string; courseVersionId: string | null; courseCode: string | null; courseTitle: string | null;
  catalogYear: string | null; courseRequired: boolean | null; pageCount: number; current: boolean; createdAt: string;
}

/** OPEN: waiting for an answer. ANSWERED: answered and back with the reviewer. ALL is a filter only. */
export type EvidenceRequestStatus = 'OPEN' | 'ANSWERED';
export interface EvidenceResponseSummary { id: string; providedBy: string; providedByName: string; createdAt: string; filename: string; pageNumber: number; }
/** GET /programs/{id}/evidence-requests: one requirement for which a reviewer asked the program for evidence. */
export interface EvidenceRequest {
  mappingId: string; jurisdiction: string; requirementRef: string; requirementTitle: string; status: EvidenceRequestStatus; stale: boolean;
  /** FIX-3: the requirement's assessment and jurisdiction already have a signed determination, so the server refuses an answer. */
  signed: boolean;
  requestNote: string | null; requestedBy: string; requestedByName: string | null; requestedAt: string;
  responseCount: number; latestResponse: EvidenceResponseSummary | null;
}
/** GET /mappings/{id}/evidence-responses: a person's answer with the verified passage it cites (page-relative offsets). */
export interface EvidenceResponse {
  id: string; reviewEventId: string; documentId: string; filename: string; kind: string; courseCode: string | null; courseRequired: boolean | null;
  pageNumber: number; charStart: number; charEnd: number; quote: string; comment: string | null; providedBy: string; providedByName: string; createdAt: string;
}
/** POST /mappings/{id}/evidence-responses: the server verifies the quote against the stored page (product rule 2). */
export interface EvidenceResponseBody { documentId: string; pageNumber: number; quote: string; comment: string | null; }

/** GET /work-queue (F13): what needs the signed-in user. Program fields are null for jurisdiction-wide and tenant-wide kinds. */
export type WorkQueueKind = 'REQUIREMENTS_TO_REVIEW' | 'EVIDENCE_REQUESTS_OPEN' | 'RULE_VERSIONS_TO_APPROVE' | 'EXTRACTION_FAILED' | 'CHANGES_OPEN' | 'READY_TO_SIGN' | 'ASSESSMENTS_FAILED';
export interface WorkItem {
  kind: WorkQueueKind; programId: string | null; programName: string | null; jurisdictionCode: string | null; count: number; oldestAt: string;
  ids: { assessmentId?: string; mappingIds?: string[]; blockedCount?: number; ruleVersionIds?: string[]; changeKind?: string; changeEventIds?: string[]; programIds?: string[]; assessmentIds?: string[] };
}
export interface WorkQueue { generatedAt: string; counts: Partial<Record<WorkQueueKind, number>>; items: WorkItem[]; }

/** F10a: where the evidence of a requirement sits. SYSTEM is the mapping's own verified span, PERSON an evidence response. */
export type CoverageSource = 'SYSTEM' | 'PERSON';
/** Descriptive flags with fixed names (D-392); they say where the evidence sits and never judge it. */
export type CoverageRisk = 'NONE' | 'OUTDATED_EVIDENCE' | 'NO_EVIDENCE' | 'ELECTIVE_ONLY' | 'SINGLE_COURSE';
/** A current course version of the program: a column of the matrix. */
export interface CoverageCourse { courseId: string; courseVersionId: string; code: string; title: string; catalogYear: string; required: boolean; }
export interface CoverageCell { courseVersionId: string; source: CoverageSource; quoteCount: number; }
/** Evidence on a superseded course version; `supersededBy` is the current version, a column of `courses` (D-394). */
export interface CoverageOutdatedCell { courseVersionId: string; code: string; catalogYear: string; supersededBy: string; source: CoverageSource; quoteCount: number; }
export interface CoverageProgramDocument { documentId: string; filename: string; kind: string; source: CoverageSource; }
export interface CoverageRequirement {
  mappingId: string; ref: string; title: string; type: RequirementType; finding: Finding; decision: DecisionKind | null;
  cells: CoverageCell[]; outdatedCells: CoverageOutdatedCell[]; programLevel: CoverageProgramDocument[]; risk: CoverageRisk;
}
/** GET /programs/{id}/coverage?jurisdiction=XX. `assessmentId` and `ruleVersion` are null before the first finished assessment. */
export interface CoverageMatrix {
  program: { id: string; name: string }; jurisdiction: { code: string; name: string }; assessmentId: string | null;
  ruleVersion: { id: string; label: string } | null; courses: CoverageCourse[]; requirements: CoverageRequirement[];
}
