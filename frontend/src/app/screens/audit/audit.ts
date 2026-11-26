import { SlicePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { actionLabel } from '../../core/labels';
import { AuditEntry } from '../../core/models';

@Component({
  selector: 'lx-audit',
  imports: [SlicePipe],
  templateUrl: './audit.html',
})
export class Audit {
  private readonly api = inject(ApiService);
  readonly entries = signal<AuditEntry[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly label = actionLabel;

  constructor() {
    firstValueFrom(this.api.audit(500)).then((e) => this.entries.set(e)).catch(() => this.error.set('The audit trail could not be loaded.'));
  }

  summary(e: AuditEntry): string {
    const d = e.detail;
    const parts: string[] = [];
    for (const key of ['jurisdiction', 'requirementRef', 'kind', 'outcome', 'filename', 'label', 'name', 'email', 'note']) {
      if (d[key] !== undefined && d[key] !== '') { parts.push(`${key}: ${d[key]}`); }
    }
    return parts.join(' · ');
  }
}
