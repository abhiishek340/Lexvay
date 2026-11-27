export type Role = 'CONTRIBUTOR' | 'REVIEWER' | 'SIGNER' | 'ADMIN';
export type Finding = 'EVIDENCE_FOUND' | 'EVIDENCE_MISSING' | 'AMBIGUOUS';
export type DecisionKind = 'ACCEPTED' | 'EVIDENCE_REQUESTED' | 'GAP_CONFIRMED';
export type Outcome = 'MEETS' | 'DOES_NOT_MEET' | 'NOT_DETERMINED';

export interface UserView { id: string; tenantId: string; email: string; displayName: string; role: Role; }
/** GET /users: the people of the caller's tenant, for resolving ids to names. */
export interface UserSummary { id: string; displayName: string; role: Role; email: string; }
export interface SessionView { authenticated: boolean; user: UserView | null; csrfHeaderName: string | null; tenantName: string | null; demo: boolean; }

export interface Program { id: string; name: string; licenseType: string; catalogYear: string; jurisdictions: string[]; createdAt: string; }
export interface Jurisdiction { id: string; code: string; name: string; }

export interface JurisdictionSummary { jurisdiction: string; total: number; evidenceFound: number; evidenceMissing: number; ambiguous: number; decided: number; }
export interface AssessmentView { id: string; programId: string; status: 'QUEUED' | 'RUNNING' | 'FINISHED' | 'FAILED'; startedAt: string | null; finishedAt: string | null; llmModel: string; promptVersion: string; jurisdictions: JurisdictionSummary[]; createdAt: string; }

export interface RequirementView { id: string; ref: string; title: string; text: string; type: string; numericField: string | null; numericOperator: string | null; numericValue: number | null; }
export interface Evidence { spanId: string; documentId: string; filename: string; kind: string; courseVersionId: string | null; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; charStart: number; charEnd: number; quote: string; }
export interface Decision { kind: DecisionKind; note: string | null; userId: string; userDisplayName: string | null; at: string; }
export interface Mapping { id: string; assessmentId: string; jurisdictionId: string; jurisdiction: string; ruleVersionId: string; requirement: RequirementView; finding: Finding; method: 'RULES_ENGINE' | 'MATCH'; rationale: string; stale: boolean; evidence: Evidence | null; decision: Decision | null; }
export interface MappingsView { assessmentId: string; jurisdiction: string | null; mappings: Mapping[]; }

export interface Determination { id: string; programId: string; jurisdiction: string; ruleVersionId: string; ruleVersionLabel: string; assessmentId: string; outcome: Outcome; signedBy: string; signedByName: string; signedAt: string; status: 'SIGNED' | 'REOPENED'; versionsHash: string; courseVersionIds: string[]; }
export interface ChangeEvent {
  id: string; kind: 'RULE_VERSION' | 'COURSE_VERSION'; oldId: string; newId: string; status: 'OPEN' | 'HANDLED'; createdAt: string;
  jurisdiction: string | null; oldLabel: string | null; newLabel: string | null; newApproved: boolean;
  courseCode: string | null; oldCatalogYear: string | null; newCatalogYear: string | null; newVersionHasDocument: boolean;
  reopenedDeterminationIds: string[];
}
export interface ReportStatusView { id: string; determinationId: string; status: 'QUEUED' | 'READY' | 'FAILED'; }
export interface AuditEntry { id: string; userId: string | null; action: string; entityType: string; entityId: string | null; detail: Record<string, unknown>; createdAt: string; }
export interface RuleVersionView { id: string; jurisdiction: string; licenseType: string; label: string; sourceUrl: string; effectiveFrom: string; approval: { by: string; at: string } | null; requirementCount: number; }
/** GET /programs/{id}/overview: the program, its latest finished assessment and determinations in one read (D-111). */
export interface ProgramOverview { program: Program; assessment: AssessmentView | null; determinations: Determination[]; openChangeEvents: ChangeEvent[]; undecidedTotal: number; }
/** GET /documents/{id}/pages/{n}: one immutable page of extracted text with its document and course context. */
export interface DocumentPage { documentId: string; filename: string; kind: string; courseCode: string | null; courseRequired: boolean | null; pageNumber: number; pageCount: number; text: string; }
