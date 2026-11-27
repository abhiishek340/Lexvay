import { formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Outcome, ReportCheck } from '../../core/models';
import { problemDetail } from '../../core/problem';

/** How much of the SHA-256 the dialog prints; the copy button copies all 64 characters. */
export const SHORT_HASH_LENGTH = 16;

/** The fingerprint every result shows: the full SHA-256 and its first characters. */
export interface Fingerprint { readonly sha256: string; readonly short: string }

/**
 * What the dialog shows for a checked file (ROADMAP F14, "Results"). Each view states facts about the file and never
 * says whether a determination is right: a match with a signed determination carries the seal preview and three
 * facts; a match whose determination was reopened says so and has no seal, because a reopened record is not
 * presented as standing; no match says only that no report of this workspace has exactly this content.
 */
export type CheckView =
  | {
      readonly kind: 'signed'; readonly headline: string; readonly place: string; readonly signature: string; readonly ruleVersion: string;
      readonly seal: { readonly outcome: Outcome; readonly signer: string; readonly signedAt: string };
      readonly report: { readonly programId: string; readonly jurisdiction: string };
      readonly fingerprint: Fingerprint;
    }
  | { readonly kind: 'reopened'; readonly headline: string; readonly place: string; readonly fingerprint: Fingerprint }
  | { readonly kind: 'none'; readonly headline: string; readonly fingerprint: Fingerprint };

const day = (iso: string): string => formatDate(iso, 'd MMMM y', 'en-US');

export function checkView(check: ReportCheck, jurisdictionName: (code: string) => string): CheckView {
  const fingerprint = { sha256: check.sha256, short: check.sha256.slice(0, SHORT_HASH_LENGTH) };
  const m = check.matchDetails;
  if (!check.match || !m) {
    return { kind: 'none', headline: 'No report in this workspace has exactly this content', fingerprint };
  }
  const place = `${jurisdictionName(m.jurisdictionCode)} · ${m.programName}`;
  if (m.determinationStatus === 'REOPENED') {
    const when = m.reopenedAt ? ` on ${day(m.reopenedAt)}` : '';
    return { kind: 'reopened', headline: `Matches, but this determination was reopened${when}`, place, fingerprint };
  }
  return {
    kind: 'signed',
    headline: `Matches the report generated ${day(m.generatedAt)}`,
    place,
    signature: `Signed by ${m.signedByName}, ${day(m.signedAt)}`,
    ruleVersion: m.ruleVersionLabel,
    seal: { outcome: m.outcome, signer: m.signedByName, signedAt: m.signedAt },
    report: { programId: m.programId, jurisdiction: m.jurisdictionCode },
    fingerprint,
  };
}

/** The type error shown before anything is sent: only a PDF can be a Lexvay report. The server checks the bytes. */
export function fileProblem(file: Pick<File, 'name' | 'type'>): string | null {
  return file.type === 'application/pdf' || /\.pdf$/i.test(file.name) ? null : 'Not a PDF file';
}

/**
 * The error of a failed check, in words: a file over the upload limit (413) says so; otherwise the server's problem
 * detail ("Not a PDF file" when the bytes are not a PDF), or a general sentence when there is none.
 */
export function checkProblem(e: unknown): string {
  if (e instanceof HttpErrorResponse && e.status === 413) {
    return 'The file is larger than the upload limit';
  }
  return problemDetail(e, 'The file could not be checked. Try again.');
}
