import { DeterminationStatus } from '../../core/models';

/**
 * Why the title row's downloads are not available yet, as its one meta line; null when both are. The PDF exists once a
 * determination is signed; the evidence package holds that PDF, so the server builds one only for a signed
 * determination whose report is ready (F15, 409 otherwise). A report is ready when the determination names one
 * (`latestReadyReportId`, D-515) or the screen has just generated it.
 */
export function downloadsNote(status: DeterminationStatus | null, reportReady: boolean): string | null {
  if (status === 'REOPENED') { return 'Reopened · PDF and package after signing again'; }
  if (status !== 'SIGNED') { return 'PDF and package after signing'; }
  return reportReady ? null : 'Package after the PDF';
}
