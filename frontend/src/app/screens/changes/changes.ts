import { SlicePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ChangeEvent } from '../../core/models';

@Component({
  selector: 'lx-changes',
  imports: [SlicePipe],
  templateUrl: './changes.html',
})
export class Changes {
  private readonly api = inject(ApiService);
  readonly events = signal<ChangeEvent[] | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    firstValueFrom(this.api.changeEvents()).then((e) => this.events.set(e)).catch(() => this.error.set('Change events could not be loaded.'));
  }

  kindLabel(kind: ChangeEvent['kind']): string {
    return kind === 'RULE_VERSION' ? 'New rule version' : 'New course version';
  }
}
