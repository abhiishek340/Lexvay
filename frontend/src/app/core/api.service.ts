import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import {
  AssessmentView, AuditEntry, ChangeEvent, Course, CoverageMatrix, DecisionKind, Determination, DeterminationLists, DocumentKind, DocumentPage, DocumentView,
  EvidenceRequest, EvidenceResponse, EvidenceResponseBody, Jurisdiction, Mapping, MappingsView, NewCourse, NewCourseVersion, NewProgram, NewProgramFact,
  NewRuleVersion, Outcome, Program, ProgramDocument, ProgramFact, ProgramOverview, RegisterFilter, RegisterRow, RequirementDraft, RequirementsView,
  RuleVersionDiff, RuleVersionView, SessionView, UploadResult, UserSummary, UserView, WorkQueue,
} from './models';
import { REGISTER_PATH, registerQuery } from './register';
import { ReportAnswer, readReport } from './report-answer';

/** Thin, typed wrapper over /api/v1. Holds no business rules. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  session(): Observable<SessionView> { return this.http.get<SessionView>(`${this.base}/auth/session`); }
  login(email: string, password: string): Observable<UserView> {
    const body = new HttpParams().set('username', email).set('password', password);
    return this.http.post<UserView>(`${this.base}/auth/login`, body.toString(), { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
  }
  logout(): Observable<void> { return this.http.post<void>(`${this.base}/auth/logout`, null); }

  programs(): Observable<Program[]> { return this.http.get<Program[]>(`${this.base}/programs`); }
  programOverview(id: string): Observable<ProgramOverview> { return this.http.get<ProgramOverview>(`${this.base}/programs/${id}/overview`); }
  createProgram(body: NewProgram): Observable<Program> { return this.http.post<Program>(`${this.base}/programs`, body); }
  courses(programId: string): Observable<Course[]> { return this.http.get<Course[]>(`${this.base}/programs/${programId}/courses`); }
  createCourse(programId: string, body: NewCourse): Observable<Course> { return this.http.post<Course>(`${this.base}/programs/${programId}/courses`, body); }
  /** A new catalog-year version; it opens a change event for every assessment that cited the version it supersedes. */
  addCourseVersion(courseId: string, body: NewCourseVersion): Observable<Course> { return this.http.post<Course>(`${this.base}/courses/${courseId}/versions`, body); }
  facts(programId: string): Observable<ProgramFact[]> { return this.http.get<ProgramFact[]>(`${this.base}/programs/${programId}/facts`); }
  recordFact(programId: string, body: NewProgramFact): Observable<ProgramFact> { return this.http.post<ProgramFact>(`${this.base}/programs/${programId}/facts`, body); }
  jurisdictions(): Observable<Jurisdiction[]> { return this.http.get<Jurisdiction[]>(`${this.base}/jurisdictions`); }
  ruleVersions(): Observable<RuleVersionView[]> { return this.http.get<RuleVersionView[]>(`${this.base}/rule-versions`); }
  /** 202: the version is stored and its requirement extraction runs as a job. */
  createRuleVersion(body: NewRuleVersion): Observable<{ ruleVersionId: string; jobId: string }> {
    return this.http.post<{ ruleVersionId: string; jobId: string }>(`${this.base}/rule-versions`, body);
  }
  requirements(ruleVersionId: string): Observable<RequirementsView> {
    return this.http.get<RequirementsView>(`${this.base}/rule-versions/${ruleVersionId}/requirements`);
  }
  replaceRequirements(ruleVersionId: string, requirements: RequirementDraft[]): Observable<RequirementsView> {
    return this.http.put<RequirementsView>(`${this.base}/rule-versions/${ruleVersionId}/requirements`, { requirements });
  }
  approveRequirements(ruleVersionId: string): Observable<RuleVersionView> {
    return this.http.post<RuleVersionView>(`${this.base}/rule-versions/${ruleVersionId}/requirements/approve`, null);
  }
  /** Without against, the server compares with the version this one supersedes. */
  ruleVersionDiff(ruleVersionId: string, against: string | null): Observable<RuleVersionDiff> {
    return this.http.get<RuleVersionDiff>(`${this.base}/rule-versions/${ruleVersionId}/diff`, { params: against ? { against } : {} });
  }

  assessment(id: string): Observable<AssessmentView> { return this.http.get<AssessmentView>(`${this.base}/assessments/${id}`); }
  /** Without a jurisdiction, every mapping of the assessment (the command palette's requirement list). */
  mappings(assessmentId: string, jurisdiction: string | null): Observable<MappingsView> {
    return this.http.get<MappingsView>(`${this.base}/assessments/${assessmentId}/mappings`, { params: jurisdiction ? { jurisdiction } : {} });
  }
  /** 202: the assessment runs as a job; it only proposes findings. */
  startAssessment(programId: string): Observable<{ assessmentId: string; jobId: string }> {
    return this.http.post<{ assessmentId: string; jobId: string }>(`${this.base}/programs/${programId}/assessments`, null);
  }
  review(mappingId: string, kind: DecisionKind, note: string | null): Observable<Mapping> {
    return this.http.post<Mapping>(`${this.base}/mappings/${mappingId}/reviews`, { kind, note });
  }

  /** Every request of the latest finished assessment, open and answered; the screens filter it. */
  evidenceRequests(programId: string): Observable<EvidenceRequest[]> {
    return this.http.get<EvidenceRequest[]>(`${this.base}/programs/${programId}/evidence-requests`, { params: { status: 'ALL' } });
  }
  evidenceResponses(mappingId: string): Observable<EvidenceResponse[]> {
    return this.http.get<EvidenceResponse[]>(`${this.base}/mappings/${mappingId}/evidence-responses`);
  }
  provideEvidence(mappingId: string, body: EvidenceResponseBody): Observable<EvidenceResponse> {
    return this.http.post<EvidenceResponse>(`${this.base}/mappings/${mappingId}/evidence-responses`, body);
  }

  /** Which course carries the evidence of each requirement of one target state (F10a); read only. */
  coverage(programId: string, jurisdiction: string): Observable<CoverageMatrix> {
    return this.http.get<CoverageMatrix>(`${this.base}/programs/${programId}/coverage`, { params: { jurisdiction } });
  }

  determinations(programId: string): Observable<Determination[]> { return this.http.get<Determination[]>(`${this.base}/programs/${programId}/determinations`); }
  sign(programId: string, jurisdiction: string, outcome: Outcome): Observable<Determination> {
    return this.http.post<Determination>(`${this.base}/programs/${programId}/determinations`, { jurisdiction, outcome });
  }

  register(filter: RegisterFilter): Observable<RegisterRow[]> { return this.http.get<RegisterRow[]>(REGISTER_PATH, { params: registerQuery(filter) }); }
  determinationLists(programId: string): Observable<DeterminationLists> {
    return this.http.get<DeterminationLists>(`${this.base}/programs/${programId}/determination-lists`);
  }

  changeEvents(): Observable<ChangeEvent[]> { return this.http.get<ChangeEvent[]>(`${this.base}/change-events`); }
  rerunChange(id: string): Observable<{ changeEventId: string; jobId: string }> {
    return this.http.post<{ changeEventId: string; jobId: string }>(`${this.base}/change-events/${id}/rerun`, null);
  }
  requestReport(determinationId: string): Observable<{ reportId: string; jobId: string }> {
    return this.http.post<{ reportId: string; jobId: string }>(`${this.base}/determinations/${determinationId}/report`, null);
  }
  /** The status while the PDF is generated, then the PDF itself (D-069, readReport). */
  report(reportId: string): Observable<ReportAnswer> {
    return this.http.get(`${this.base}/reports/${reportId}`, { observe: 'response', responseType: 'blob' }).pipe(switchMap(readReport));
  }
  jobStatus(jobId: string): Observable<{ id: string; status: string; error: string | null }> {
    return this.http.get<{ id: string; status: string; error: string | null }>(`${this.base}/jobs/${jobId}`);
  }
  workQueue(): Observable<WorkQueue> { return this.http.get<WorkQueue>(`${this.base}/work-queue`); }
  workQueueCount(): Observable<{ total: number }> { return this.http.get<{ total: number }>(`${this.base}/work-queue/count`); }
  users(): Observable<UserSummary[]> { return this.http.get<UserSummary[]>(`${this.base}/users`); }
  /** Newest first; `filters` are the query parameters of audit-view's auditParams, applied by the server (D-116). */
  audit(filters: Record<string, string>, limit: number, offset: number): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.base}/audit`, { params: { ...filters, limit, offset } });
  }

  /** 202: the file is stored and its text is read by a job; GET /documents/{id} tells how far it got. */
  uploadDocument(programId: string, file: File, kind: DocumentKind, courseVersionId: string | null): Observable<UploadResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    const params: Record<string, string> = courseVersionId ? { kind, courseVersionId } : { kind };
    return this.http.post<UploadResult>(`${this.base}/programs/${programId}/documents`, form, { params });
  }
  document(documentId: string): Observable<DocumentView> { return this.http.get<DocumentView>(`${this.base}/documents/${documentId}`); }
  programDocuments(programId: string): Observable<ProgramDocument[]> { return this.http.get<ProgramDocument[]>(`${this.base}/programs/${programId}/documents`); }
  /** Immutable and cached by the browser for a year (D-112); read through DocumentPageCache. */
  documentPage(documentId: string, pageNumber: number): Observable<DocumentPage> {
    return this.http.get<DocumentPage>(`${this.base}/documents/${documentId}/pages/${pageNumber}`);
  }
  /** The original uploaded file, opened by the browser in a new tab. */
  documentFileUrl(documentId: string): string { return `${this.base}/documents/${documentId}/file`; }
}
