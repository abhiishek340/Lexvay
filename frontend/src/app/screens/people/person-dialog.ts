import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, effect, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Account, Role, UserSetup } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { PersonProblems, ROLE_DESCRIPTION, ROLE_OPTIONS, expiryText, personProblems, setupLink } from './people-view';

type Phase = 'form' | 'code';

/**
 * Add person, then the one-time setup link (DESIGN.md word budget; F5b point 2). "New setup code" opens the same
 * link view for an existing person. The code lives in this component's state while the dialog is open and nowhere
 * else: never in storage or a log, and cleared when the dialog closes.
 */
@Component({
  selector: 'lx-person-dialog',
  imports: [FormsModule, ButtonDirective, ButtonLabel, Dialog, InputText, Message, Select, Skeleton],
  templateUrl: './person-dialog.html',
  styleUrl: './person-dialog.css',
})
export class PersonDialog {
  private readonly api = inject(ApiService);
  /** The account as the server now has it (created, or unchanged after a new code), for the list. */
  readonly saved = output<Account>();

  readonly roles = ROLE_OPTIONS;
  /** What a role may do, under its name in the role options. */
  describe(role: Role): string {
    return ROLE_DESCRIPTION[role];
  }

  readonly visible = signal(false);
  readonly phase = signal<Phase>('form');
  readonly name = signal('');
  readonly email = signal('');
  readonly role = signal<Role>('CONTRIBUTOR');
  readonly problems = signal<PersonProblems>({});
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  /** Whom the link is for, known before the code arrives. */
  readonly person = signal<Pick<Account, 'displayName' | 'email'> | null>(null);
  /** A replacement code: earlier links of the person stop working. */
  readonly replaced = signal(false);
  readonly copied = signal(false);
  private readonly setup = signal<UserSetup | null>(null);

  readonly link = computed(() => {
    const setup = this.setup();
    return setup ? setupLink(location.origin, setup.setupCode) : null;
  });
  readonly expiry = computed(() => {
    const setup = this.setup();
    return setup ? expiryText(setup.expiresAt, new Date()) : '';
  });

  private readonly copyButton = viewChild<ElementRef<HTMLButtonElement>>('copyButton');
  private copiedTimer = 0;

  constructor() {
    // Once the link is on screen, focus moves to Copy: the next thing to do, and where a screen reader starts reading.
    effect(() => this.copyButton()?.nativeElement.focus());
  }

  add(): void {
    this.reset();
    this.phase.set('form');
    this.visible.set(true);
  }

  /** Issues a new code for the person and shows it; their earlier unused codes stop working (D-372). */
  async issue(account: Account): Promise<void> {
    this.reset();
    this.phase.set('code');
    this.person.set(account);
    this.replaced.set(true);
    this.visible.set(true);
    this.busy.set(true);
    try {
      this.show(await firstValueFrom(this.api.issueSetupCode(account.id)));
    } catch (e: unknown) {
      this.error.set(problemDetail(e, 'A new setup code could not be issued.'));
    } finally {
      this.busy.set(false);
    }
  }

  async submit(): Promise<void> {
    if (this.busy()) { return; }
    const problems = personProblems(this.name(), this.email());
    this.problems.set(problems);
    this.error.set(null);
    if (problems.name || problems.email) { return; }
    this.busy.set(true);
    try {
      const setup = await firstValueFrom(this.api.createUser({ displayName: this.name().trim(), email: this.email().trim(), role: this.role() }));
      this.person.set(setup.user);
      this.phase.set('code');
      this.show(setup);
    } catch (e: unknown) {
      const detail = problemDetail(e, 'The person could not be added.');
      // 409: the email belongs to an account (here or at another institution); it goes under the field.
      if (e instanceof HttpErrorResponse && e.status === 409) {
        this.problems.set({ email: detail });
      } else {
        this.error.set(detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  async copy(): Promise<void> {
    const link = this.link();
    if (!link) { return; }
    try {
      await navigator.clipboard.writeText(link);
      this.copied.set(true);
      clearTimeout(this.copiedTimer);
      this.copiedTimer = window.setTimeout(() => this.copied.set(false), 2000);
    } catch {
      this.error.set('The link could not be copied. Select it and copy it by hand.');
    }
  }

  /** Closing forgets everything the dialog held, the code first. */
  reset(): void {
    clearTimeout(this.copiedTimer);
    this.setup.set(null);
    this.copied.set(false);
    this.person.set(null);
    this.replaced.set(false);
    this.name.set('');
    this.email.set('');
    this.role.set('CONTRIBUTOR');
    this.problems.set({});
    this.error.set(null);
  }

  private show(setup: UserSetup): void {
    this.setup.set(setup);
    this.saved.emit(setup.user);
  }
}
