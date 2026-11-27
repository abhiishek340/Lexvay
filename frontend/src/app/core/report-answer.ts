import { HttpResponse } from '@angular/common/http';
import { ReportStatusView } from './models';

/** What `GET /reports/{id}` answered: still being generated, failed, or the PDF itself with its file name. */
export type ReportAnswer = { status: 'QUEUED' | 'FAILED' } | { status: 'READY'; pdf: Blob; filename: string };

/** `attachment; filename="lexvay-evidence-report-….pdf"` gives the quoted name; anything else null. */
export function attachmentName(header: string | null): string | null {
  return /filename="([^"]+)"/.exec(header ?? '')?.[1] ?? null;
}

/**
 * The endpoint answers with the PDF once it is ready and with its status as JSON before that (D-069), so one request
 * both polls and, at the end, delivers the file: the caller never asks for the PDF a second time.
 */
export async function readReport(response: HttpResponse<Blob>): Promise<ReportAnswer> {
  const body = response.body ?? new Blob();
  if ((response.headers.get('Content-Type') ?? '').startsWith('application/pdf')) {
    return { status: 'READY', pdf: body, filename: attachmentName(response.headers.get('Content-Disposition')) ?? 'lexvay-evidence-report.pdf' };
  }
  const view = JSON.parse(await body.text()) as ReportStatusView;
  return { status: view.status === 'FAILED' ? 'FAILED' : 'QUEUED' };
}
