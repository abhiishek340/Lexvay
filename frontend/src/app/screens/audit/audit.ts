import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { problemDetail } from '../../core/problem';
import { AuditRow, auditRows, filterOptions, filterRows } from './audit-view';

/** The backend returns at most 500 entries per request; this screen shows the newest 500. */
const LIMIT = 500;

@Component({
  selector: 'lx-audit',
  imports: [DatePipe, FormsModule, Message, Select, Skeleton, TableModule],
  templateUrl: './audit.html',
  styleUrl: './audit.css',
})
export class Audit {
  private readonly api = inject(ApiService);
  readonly limit = LIMIT;
  readonly rows = signal<AuditRow[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly action = signal<string | null>(null);
  readonly entityType = signal<string | null>(null);

  readonly actions = computed(() => filterOptions(this.rows() ?? [], (r) => [r.action, r.actionLabel]));
  readonly entityTypes = computed(() => filterOptions(this.rows() ?? [], (r) => [r.entityType, r.entityLabel]));
  readonly visible = computed(() => filterRows(this.rows() ?? [], { action: this.action(), entityType: this.entityType() }));

  constructor() {
    Promise.all([firstValueFrom(this.api.audit(LIMIT)), firstValueFrom(this.api.users())])
      .then(([entries, users]) => this.rows.set(auditRows(entries, users)))
      .catch((e: unknown) => this.error.set(problemDetail(e, 'The audit trail could not be loaded.')));
  }
}
