export type Role = 'CONTRIBUTOR' | 'REVIEWER' | 'SIGNER' | 'ADMIN';
export type Finding = 'EVIDENCE_FOUND' | 'EVIDENCE_MISSING' | 'AMBIGUOUS';
export type DecisionKind = 'ACCEPTED' | 'EVIDENCE_REQUESTED' | 'GAP_CONFIRMED';
export type Outcome = 'MEETS' | 'DOES_NOT_MEET' | 'NOT_DETERMINED';
/** Where a page's stored text came from (F11); OCR pages carry a confidence from 0 to 100. */
export type TextSource = 'TEXT_LAYER' | 'OCR';

export interface UserView { id: string; tenantId: string; email: string; displayName: string; role: Role; }
/** GET /users: the people of the caller's tenant, for resolving ids to names. */
export interface UserSummary { id: string; displayName: string; role: Role; email: string; }
export interface SessionView { authenticated: boolean; user: UserView | null; csrfHeaderName: string | null; tenantName: string | null; demo: boolean; }

export interface Program { id: string; name: string; licenseType: string; catalogYear: string; jurisdictions: string[]; createdAt: string; }
export interface Jurisdiction { id: string; code: string; name: string; }

export interface JurisdictionSummary { jurisdiction: string; total: number; evidenceFound: number; evidenceMissing: number; ambiguous: number; decided: number; }
export interface AssessmentView { id: string; programId: string; status: 'QUEUED' | 'RUNNING' | 'FINISHED' | 'FAILED'; startedAt: string | null; finishedAt: string | null; llmModel: string; promptVersion: string; jurisdictions: JurisdictionSummary[]; createdAt: string; }

export type RequirementType = 'NUMERIC' | 'SUBJECT' | 'SUPERVISED_HOURS' | 'ACCREDITATION' | 'JURISDICTION_SPECIFIC';
export type NumericField = 'TOTAL_CREDITS' | 'PRACTICUM_HOURS' | 'INTERNSHIP_HOURS';
export type NumericOperator = '>=' | '<=' | '=' | '>' | '<';
export interface RequirementView { id: string; ref: string; title: string; text: string; type: RequirementType; numericField: NumericField | null; numericOperator: NumericOperator | null; numericValue: number | null; }
export interface Evidence { spanId: string; documentId: string; filename: string; kind: string; courseVersionId: string | null; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; charStart: number; charEnd: number; quote: string; textSource: TextSource; ocrConfidence: number | null; }
export interface Decision { kind: DecisionKind; note: string | null; userId: string; userDisplayName: string | null; at: string; }
export interface Mapping { id: string; assessmentId: string; jurisdictionId: string; jurisdiction: string; ruleVersionId: string; requirement: RequirementView; finding: Finding; method: 'RULES_ENGINE' | 'MATCH'; rationale: string; stale: boolean; evidence: Evidence | null; decision: Decision | null; }
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
}

export type DiffOp = 'EQUAL' | 'DELETE' | 'INSERT';
export interface DiffSegment { op: DiffOp; text: string; }
export type DiffStatus = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
export type DiffAspect = 'TITLE' | 'TEXT' | 'TYPE' | 'NUMERIC';
export interface DiffRow { status: DiffStatus; refChanged: boolean; before: RequirementView | null; after: RequirementView | null; changes: DiffAspect[]; textDiff: DiffSegment[] | null; }
export interface RuleVersionDiff {
  from: { id: string; label: string; effectiveFrom: string }; to: { id: string; label: string; effectiveFrom: string };
  summary: { added: number; removed: number; changed: number; unchanged: number }; requirements: DiffRow[]; ruleTextDiff: DiffSegment[];
}
export interface ProgramOverview { program: Program; assessment: AssessmentView | null; determinations: Determination[]; openChangeEvents: ChangeEvent[]; undecidedTotal: number; }
/** GET /documents/{id}/pages/{n}: one immutable page of extracted text. */
export interface DocumentPage { documentId: string; filename: string; kind: string; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; pageCount: number; text: string; textSource: TextSource; ocrConfidence: number | null; }

/** GET /work-queue (F13): what needs the signed-in user. Program fields are null for jurisdiction-wide and tenant-wide kinds. */
export type WorkQueueKind = 'REQUIREMENTS_TO_REVIEW' | 'EVIDENCE_REQUESTS_OPEN' | 'RULE_VERSIONS_TO_APPROVE' | 'EXTRACTION_FAILED' | 'CHANGES_OPEN' | 'READY_TO_SIGN' | 'ASSESSMENTS_FAILED';
export interface WorkItem {
  kind: WorkQueueKind; programId: string | null; programName: string | null; jurisdictionCode: string | null; count: number; oldestAt: string;
  ids: { assessmentId?: string; mappingIds?: string[]; blockedCount?: number; ruleVersionIds?: string[]; changeKind?: string; changeEventIds?: string[]; programIds?: string[]; assessmentIds?: string[] };
}
export interface WorkQueue { generatedAt: string; counts: Partial<Record<WorkQueueKind, number>>; items: WorkItem[]; }
