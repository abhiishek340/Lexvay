import { Component, ElementRef, computed, effect, inject, model, signal, untracked, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  phosphorArrowElbowDownLeft, phosphorBuildings, phosphorDownloadSimple, phosphorMapTrifold, phosphorPlay, phosphorPlus, phosphorTextAlignLeft,
} from '@ng-icons/phosphor-icons/regular';
import { phosphorMagnifyingGlassDuotone } from '@ng-icons/phosphor-icons/duotone';
import auraCommandmenu from '@primeuix/themes/aura/commandmenu';
import auraMenu from '@primeuix/themes/aura/menu';
import { MenuItem, MessageService } from 'primeng/api';
import { CommandMenu } from 'primeng/commandmenu';
import { Dialog } from 'primeng/dialog';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ProgramContext } from '../../core/program-context';
import { problemDetail } from '../../core/problem';
import { NO_FILTER, registerCsvUrl } from '../../core/register';
import { SessionService } from '../../core/session.service';
import { registerComponentTokens } from '../../theme/component-tokens';
import { lexvayDialog } from '../../theme/dialog-tokens';
import { lexvayTokens as t } from '../../theme/lexvay-preset';
import { Command, CommandRun, PaletteAction, paletteCommands, paletteSections, pushRecent } from './commands';
import { commandScore } from './fuzzy';

/** The palette's icons beyond SHELL_ICONS (which already holds every screen's rail icon and the search glass). */
export const PALETTE_ICONS = {
  phosphorArrowElbowDownLeft, phosphorBuildings, phosphorDownloadSimple, phosphorMapTrifold, phosphorPlay, phosphorPlus, phosphorTextAlignLeft,
  phosphorMagnifyingGlassDuotone,
};

const PALETTE_TOKENS = {
  dialog: lexvayDialog,
  menu: { ...auraMenu, root: { ...auraMenu.root, background: 'transparent', borderColor: 'transparent' } },
  commandmenu: {
    ...auraCommandmenu,
    root: { ...auraCommandmenu.root, background: 'transparent', borderColor: 'transparent', borderRadius: '0', height: 'min(26rem, 70vh)' },
    header: { ...auraCommandmenu.header, padding: '0.5rem 1rem', background: 'transparent', borderColor: t.line },
    input: { ...auraCommandmenu.input, padding: '0.75rem 0', fontSize: '1rem', color: t.text, placeholderColor: t.text2 },
    list: { ...auraCommandmenu.list, padding: '0.5rem' },
    empty: { ...auraCommandmenu.empty, padding: '2.5rem 0', color: t.text2 },
    footer: { ...auraCommandmenu.footer, padding: '0.625rem 1rem', background: 'transparent', borderColor: t.line },
  },
};

const JOB_POLL_MS = 2000;
const JOB_POLL_LIMIT = 150;

/** A palette entry as PrimeNG's CommandMenu takes it; the command itself rides along for the item template. */
interface PaletteItem extends MenuItem { command$: Command }

/**
 * The command palette (Ctrl K or Cmd K anywhere, or the search field in the top bar; DESIGN.md "CommandPalette"):
 * go to any screen, jump to a state or a requirement of the latest assessment, run an action the role allows.
 * Matching is fuzzyScore, recent commands come first (kept per user in this browser), and the first result is
 * one Enter away. The shell renders it in a @defer block, so its code, icons and token sets load with it.
 */
@Component({
  selector: 'lx-command-palette',
  imports: [NgIcon, CommandMenu, Dialog],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.css',
  viewProviders: [provideIcons(PALETTE_ICONS)],
})
export class CommandPalette {
  readonly visible = model(false);
  readonly search = signal('');

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);
  private readonly session = inject(SessionService);
  private readonly context = inject(ProgramContext);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly menu = viewChild(CommandMenu);

  /** The latest assessment's requirements, fetched the first time the palette opens for that assessment. */
  private readonly requirements = signal<{ assessmentId: string; list: { ref: string; title: string; jurisdiction: string }[] } | null>(null);
  private readonly recent = signal<string[]>([]);

  readonly commands = computed(() => {
    const program = this.context.program();
    const assessment = this.context.assessment();
    const loaded = this.requirements();
    return paletteCommands({
      programId: program?.id ?? null,
      role: this.session.user()?.role ?? null,
      states: (program?.jurisdictions ?? []).map((code) => ({ code, name: this.context.jurisdictionName(code) })),
      requirements: loaded && loaded.assessmentId === assessment?.id ? loaded.list : [],
    });
  });

  readonly model = computed<MenuItem[]>(() =>
    paletteSections(this.commands(), this.recent(), this.search().trim() !== '').map((section) => ({
      label: section.group,
      items: section.commands.map((c): PaletteItem => ({ id: c.id, label: c.label, command$: c, command: () => this.choose(c) })),
    })));

  readonly filter = (item: MenuItem, query: string): number => {
    const command = (item as PaletteItem).command$;
    return command ? commandScore(query, command.label, command.keywords) : 0;
  };

  constructor() {
    registerComponentTokens(PALETTE_TOKENS);
    effect(() => {
      if (!this.visible()) { return; }
      untracked(() => {
        this.search.set('');
        this.recent.set(readRecent(this.storageKey()));
        this.loadRequirements();
      });
    });
    // The best match is always highlighted, so Enter opens it.
    effect(() => {
      this.model();
      const menu = this.menu();
      if (this.visible() && menu) {
        requestAnimationFrame(() => menu.menuViewChild()?.changeFocusedOptionIndex(0));
      }
    });
  }

  /** CommandMenu's input names a list id that its list element does not carry; give it that id (axe aria-valid-attr-value). */
  onShow(): void {
    const input = this.host.ownerDocument.querySelector<HTMLInputElement>('.lx-palette .p-commandmenu-input');
    const list = this.host.ownerDocument.querySelector<HTMLElement>('.lx-palette .p-commandmenu-list');
    const controls = input?.getAttribute('aria-controls');
    if (list && controls) { list.id = controls; }
    input?.focus();
  }

  private choose(command: Command): void {
    const key = this.storageKey();
    const next = pushRecent(this.recent(), command.id);
    this.recent.set(next);
    writeRecent(key, next);
    this.visible.set(false);
    this.run(command.run);
  }

  private run(run: CommandRun): void {
    if (run.kind === 'navigate') {
      this.router.navigate([...run.commands], { queryParams: run.queryParams });
    } else {
      this.act(run.action);
    }
  }

  private act(action: PaletteAction): void {
    const programId = this.context.program()?.id;
    switch (action) {
      case 'download-register':
        window.location.assign(registerCsvUrl(NO_FILTER));
        break;
      case 'new-rule-version':
        if (programId) { this.router.navigate(['/programs', programId, 'rules'], { queryParams: { new: '1' } }); }
        break;
      case 'run-assessment':
        if (programId) { this.runAssessment(programId); }
        break;
    }
  }

  /** Starts an assessment, then follows its job and ends with a toast; the program's data refreshes when it is done. */
  private async runAssessment(programId: string): Promise<void> {
    try {
      const { jobId } = await firstValueFrom(this.api.startAssessment(programId));
      this.toast.add({ severity: 'info', summary: 'Assessment started' });
      for (let i = 0; i < JOB_POLL_LIMIT; i++) {
        await new Promise((resolve) => setTimeout(resolve, JOB_POLL_MS));
        const job = await firstValueFrom(this.api.jobStatus(jobId));
        if (job.status === 'DONE') {
          await this.context.refreshAfterChange();
          this.toast.add({ severity: 'success', summary: 'Assessment finished' });
          return;
        }
        if (job.status === 'FAILED') {
          this.toast.add({ severity: 'error', summary: 'Assessment failed', detail: job.error ?? undefined });
          return;
        }
      }
    } catch (e: unknown) {
      this.toast.add({ severity: 'error', summary: 'Assessment not started', detail: problemDetail(e, 'The assessment could not be started.') });
    }
  }

  private async loadRequirements(): Promise<void> {
    const assessment = this.context.assessment();
    if (!assessment || this.requirements()?.assessmentId === assessment.id) { return; }
    try {
      const view = await firstValueFrom(this.api.mappings(assessment.id, null));
      const list = view.mappings.map((m) => ({ ref: m.requirement.ref, title: m.requirement.title, jurisdiction: m.jurisdiction }));
      this.requirements.set({ assessmentId: assessment.id, list });
    } catch {
      // Requirements are a convenience here; screens and states stay searchable without them.
    }
  }

  private storageKey(): string {
    return `lexvay.palette.recent.${this.session.user()?.id ?? 'anonymous'}`;
  }
}

function readRecent(key: string): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? '[]') as unknown;
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    return [];
  }
}

function writeRecent(key: string, recent: string[]): void {
  try { localStorage.setItem(key, JSON.stringify(recent)); } catch { /* storage may be unavailable; recents then last for this page */ }
}
