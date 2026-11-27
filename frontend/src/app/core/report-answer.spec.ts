import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { attachmentName, readReport } from './report-answer';

const answer = (status: number, type: string, body: Blob, disposition?: string): HttpResponse<Blob> =>
  new HttpResponse({ status, body, headers: new HttpHeaders({ 'Content-Type': type, ...(disposition ? { 'Content-Disposition': disposition } : {}) }) });
const json = (value: unknown): Blob => new Blob([JSON.stringify(value)], { type: 'application/json' });

describe('report answer', () => {
  it('is the status while the PDF is generated or when it failed', async () => {
    expect(await readReport(answer(202, 'application/json', json({ id: 'r', determinationId: 'd', status: 'QUEUED' })))).toEqual({ status: 'QUEUED' });
    expect(await readReport(answer(200, 'application/json', json({ id: 'r', determinationId: 'd', status: 'FAILED' })))).toEqual({ status: 'FAILED' });
  });

  it('is the PDF with the name the server gives it once ready', async () => {
    const pdf = new Blob(['%PDF-1.7'], { type: 'application/pdf' });
    const ready = await readReport(answer(200, 'application/pdf', pdf, 'attachment; filename="lexvay-evidence-report-r.pdf"'));
    expect(ready).toEqual({ status: 'READY', pdf, filename: 'lexvay-evidence-report-r.pdf' });
    expect(attachmentName(null)).toBeNull();
    expect(attachmentName('inline')).toBeNull();
  });
});
