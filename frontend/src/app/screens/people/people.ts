import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { phosphorUsersDuotone } from '@ng-icons/phosphor-icons/duotone';
import {
  phosphorArrowCounterClockwise, phosphorCheck, phosphorCheckCircle, phosphorClock, phosphorCopy, phosphorDotsThree, phosphorHourglass, phosphorKey,
  phosphorProhibit, phosphorSignOut, phosphorUserGear, phosphorUserPlus,
} from '@ng-icons/phosphor-icons/regular';
import { ConfirmationService, MenuItem, MessageService } from 'primeng/api';
import { Avatar } from 'primeng/avatar';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Menu } from 'primeng/menu';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ROLE_LABEL } from '../../core/labels';
import { Account, Role } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import type { IconName } from '../../theme/icons';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { initials } from '../../ui/initials';
import { PEOPLE_TOKENS } from './people-theme';
import {
  NO_PEOPLE_QUERY, PERSON_STATUS, PeopleQuery, PersonAction, ROLE_OPTIONS, STATUS_OPTIONS, filterPeople, filtered, peopleCount, personActions,
  personStatus, upsert,
} from './people-view';
import { PersonDialog } from './person-dialog';

/** The icons People shows beyond SHELL_ICONS, its dialogs included, provided by its route (people.routes.ts). */
export const PEOPLE_ICONS = {
  phosphorArrowCounterClockwise, phosphorCheck, phosphorCheckCircle, phosphorClock, phosphorCopy, phosphorDotsThree, phosphorHourglass, phosphorKey,
  phosphorProhibit, phosphorSignOut, phosphorUserGear, phosphorUserPlus, phosphorUsersDuotone,
};

/** A change waiting for the administrator's confirmation, and what the dialog says about it. */
type Pending =
  | { readonly kind: 'role'; readonly account: Account }
  | { readonly kind: 'access'; readonly account: Account; readonly active: boolean };

const ACTION_MENU: Record<PersonAction, { label: string; icon: IconName }> = {
  'role': { label: 'Change role', icon: 'phosphorUserGear' },
  'deactivate': { label: 'Deactivate', icon: 'phosphorProhibit' },
  'reactivate': { label: 'Reactivate', icon: 'phosphorArrowCounterClockwise' },
  'setup-code': { label: 'New setup code', icon: 'phosphorKey' },
};

/**
 * People (F5b): the institution's accounts for an administrator. Add a person and hand over their one-time setup
 * link; change a role, deactivate or reactivate after a confirmation that names the consequence; issue a new setup
 * code. Nobody is deleted (D-370). The server holds every rule; this screen offers only what it would accept.
 */
@Component({
  selector: 'lx-people',
  imports: [
    DatePipe, FormsModule, NgIcon, Avatar, ButtonDirective, ButtonLabel, ConfirmDialog, IconField, InputIcon, InputText, Menu, Message, Select, Skeleton,
    TableModule, Tag, Enter, InfoTip, PersonDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './people.html',
  styleUrl: './people.css',
})
export class People {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly toast = inject(MessageService);

  readonly roleOptions = ROLE_OPTIONS;
  readonly statusOptions = STATUS_OPTIONS;
  readonly initials = initials;

  readonly accounts = signal<Account[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly query = signal<PeopleQuery>(NO_PEOPLE_QUERY);
  readonly selfId = computed(() => this.session.user()?.id ?? null);
  readonly shown = computed(() => filterPeople(this.accounts() ?? [], this.query()));
  readonly filtered = computed(() => filtered(this.query()));
  readonly count = computed(() => (this.accounts() ? peopleCount(this.shown().length, this.accounts()!.length) : ''));

  /** The row whose menu is open, and its entries. */
  readonly menuItems = signal<MenuItem[]>([]);
  readonly menuFor = signal<string | null>(null);
  readonly menuOpen = signal(false);

  readonly pending = signal<Pending | null>(null);
  readonly newRole = signal<Role>('CONTRIBUTOR');
  readonly confirmError = signal<string | null>(null);
  readonly confirmBusy = signal(false);

  private readonly menu = viewChild.required<Menu>('menu');
  private readonly dialog = viewChild.required(PersonDialog);

  constructor() {
    registerComponentTokens(PEOPLE_TOKENS);
    firstValueFrom(this.api.accounts())
      .then((accounts) => this.accounts.set(accounts))
      .catch((e: unknown) => this.error.set(problemDetail(e, 'People could not be loaded.')));
  }

  status(account: Account) {
    return PERSON_STATUS[personStatus(account)];
  }

  roleOf(account: Account): string {
    return ROLE_LABEL[account.role];
  }

  set<K extends keyof PeopleQuery>(key: K, value: PeopleQuery[K] | undefined | null): void {
    this.query.update((q) => ({ ...q, [key]: value ?? (key === 'text' ? '' : null) }));
  }

  clear(): void {
    this.query.set(NO_PEOPLE_QUERY);
  }

  add(): void {
    this.dialog().add();
  }

  saved(account: Account): void {
    this.accounts.update((list) => upsert(list ?? [], account));
  }

  openMenu(event: Event, account: Account): void {
    this.menuItems.set(personActions(account, this.selfId()).map((action): MenuItem => ({
      id: action, label: ACTION_MENU[action].label, icon: ACTION_MENU[action].icon, command: () => this.run(action, account),
    })));
    this.menuFor.set(account.id);
    this.menu().toggle(event);
  }

  private run(action: PersonAction, account: Account): void {
    if (action === 'setup-code') {
      void this.dialog().issue(account);
      return;
    }
    this.confirmError.set(null);
    if (action === 'role') {
      this.newRole.set(account.role);
      this.pending.set({ kind: 'role', account });
    } else {
      this.pending.set({ kind: 'access', account, active: action === 'reactivate' });
    }
    this.confirmation.confirm({
      key: 'people',
      header: action === 'role' ? 'Change role' : `${action === 'deactivate' ? 'Deactivate' : 'Reactivate'} ${account.displayName}?`,
      closeOnEscape: true,
      reject: () => this.pending.set(null),
    });
  }

  close(): void {
    this.confirmation.close();
    this.pending.set(null);
  }

  /** Applies the confirmed change; the server's refusal (409: last administrator, self) stays in the dialog. */
  async apply(): Promise<void> {
    const pending = this.pending();
    if (!pending || this.confirmBusy()) { return; }
    this.confirmBusy.set(true);
    this.confirmError.set(null);
    try {
      const { account } = pending;
      const updated = pending.kind === 'role'
        ? await firstValueFrom(this.api.changeRole(account.id, this.newRole()))
        : await firstValueFrom(this.api.setActive(account.id, pending.active));
      this.saved(updated);
      this.close();
      this.toast.add({ severity: 'success', summary: changedSummary(pending, updated) });
    } catch (e: unknown) {
      this.confirmError.set(problemDetail(e, 'The change could not be saved.'));
    } finally {
      this.confirmBusy.set(false);
    }
  }
}

function changedSummary(pending: Pending, updated: Account): string {
  if (pending.kind === 'role') { return `${updated.displayName} is now ${updated.role === 'ADMIN' ? 'an' : 'a'} ${ROLE_LABEL[updated.role].toLowerCase()}.`; }
  return pending.active ? `${updated.displayName} can sign in again.` : `${updated.displayName} can no longer sign in.`;
}
