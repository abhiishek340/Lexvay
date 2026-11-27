import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AssessmentView, AuditEntry, ChangeEvent, DecisionKind, Determination, DocumentPage, Jurisdiction, Mapping, MappingsView, NewRuleVersion, Outcome, Program,
  ReportStatusView, RequirementDraft, RequirementsView, RuleVersionDiff, RuleVersionView, SessionView, UserSummary, UserView,
} from './models';

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
  program(id: string): Observable<Program> { return this.http.get<Program>(`${this.base}/programs/${id}`); }
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
  documentPage(documentId: string, pageNumber: number): Observable<DocumentPage> {
    return this.http.get<DocumentPage>(`${this.base}/documents/${documentId}/pages/${pageNumber}`);
  }

  assessment(id: string): Observable<AssessmentView> { return this.http.get<AssessmentView>(`${this.base}/assessments/${id}`); }
  mappings(assessmentId: string, jurisdiction: string): Observable<MappingsView> {
    return this.http.get<MappingsView>(`${this.base}/assessments/${assessmentId}/mappings`, { params: { jurisdiction } });
  }
  review(mappingId: string, kind: DecisionKind, note: string | null): Observable<Mapping> {
    return this.http.post<Mapping>(`${this.base}/mappings/${mappingId}/reviews`, { kind, note });
  }

  determinations(programId: string): Observable<Determination[]> { return this.http.get<Determination[]>(`${this.base}/programs/${programId}/determinations`); }
  sign(programId: string, jurisdiction: string, outcome: Outcome): Observable<Determination> {
    return this.http.post<Determination>(`${this.base}/programs/${programId}/determinations`, { jurisdiction, outcome });
  }

  changeEvents(): Observable<ChangeEvent[]> { return this.http.get<ChangeEvent[]>(`${this.base}/change-events`); }
  rerunChange(id: string): Observable<{ changeEventId: string; jobId: string }> {
    return this.http.post<{ changeEventId: string; jobId: string }>(`${this.base}/change-events/${id}/rerun`, null);
  }
  requestReport(determinationId: string): Observable<{ reportId: string; jobId: string }> {
    return this.http.post<{ reportId: string; jobId: string }>(`${this.base}/determinations/${determinationId}/report`, null);
  }
  /** 202 with JSON while generating; the PDF itself is downloaded through the browser from reportUrl. */
  reportStatus(reportId: string): Observable<ReportStatusView> {
    return this.http.get<ReportStatusView>(`${this.base}/reports/${reportId}`, { headers: { Accept: 'application/json' } });
  }
  reportUrl(reportId: string): string { return `${this.base}/reports/${reportId}`; }
  jobStatus(jobId: string): Observable<{ id: string; status: string; error: string | null }> {
    return this.http.get<{ id: string; status: string; error: string | null }>(`${this.base}/jobs/${jobId}`);
  }
  users(): Observable<UserSummary[]> { return this.http.get<UserSummary[]>(`${this.base}/users`); }
  audit(limit = 200): Observable<AuditEntry[]> { return this.http.get<AuditEntry[]>(`${this.base}/audit`, { params: { limit } }); }

  /** The latest finished assessment is found through the audit trail's ASSESSMENT_FINISHED entries. */
  latestAssessmentId(entries: AuditEntry[], programId: string): string | null {
    const started = new Set(entries.filter((e) => e.action === 'ASSESSMENT_STARTED' && e.detail['programId'] === programId).map((e) => e.entityId));
    const finished = entries.find((e) => e.action === 'ASSESSMENT_FINISHED' && e.entityId && started.has(e.entityId));
    return finished?.entityId ?? null;
  }
}
