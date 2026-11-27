import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { phosphorGridFourDuotone } from '@ng-icons/phosphor-icons/duotone';
import { phosphorBook, phosphorCalculator, phosphorCircleDashed, phosphorClock, phosphorWarningCircle } from '@ng-icons/phosphor-icons/regular';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Popover } from 'primeng/popover';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tooltip } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CoverageMatrix } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { InfoTip } from '../../ui/info-tip';
import { StatePill, StatePills } from '../../ui/state-pills';
import { COVERAGE_TOKENS } from './coverage-theme';
import { Cell, FLAGS, Flag, MoveKey, Position, Row, coverageView, firstCell, flagCounts, matchesFlag, move, unflaggedShare } from './coverage-view';

/** The icons the Coverage screen shows beyond SHELL_ICONS, provided by its route (coverage.routes.ts). */
export const COVERAGE_ICONS = { phosphorBook, phosphorCalculator, phosphorCircleDashed, phosphorClock, phosphorGridFourDuotone, phosphorWarningCircle };

const MOVES: Record<string, MoveKey> = { ArrowLeft: 'ArrowLeft', ArrowRight: 'ArrowRight', ArrowUp: 'ArrowUp', ArrowDown: 'ArrowDown', Home: 'Home', End: 'End' };
/** The matrix enters column by column, 20 ms apart, at most twelve staggered (DESIGN.md motion, F10b point 4). */
const STAGGER_CAP = 11;

/**
 * Coverage (F10b): for one target state, which course carries the evidence of each requirement, and where a
 * requirement rests on an elective, on one course, on outdated evidence or on nothing. The matrix reports the evidence
 * and the flags the server computed (D-392); it concludes nothing (product rule 1). One request per state: the state
 * in the URL first, then the others for their pill rings; each answer is kept while the screen is open.
 */
@Component({
  selector: 'lx-coverage',
  imports: [FormsModule, RouterLink, NgIcon, ButtonDirective, ButtonLabel, Message, Popover, SelectButton, Skeleton, TabPanel, TabPanels, Tabs, Tooltip, InfoTip, StatePills],
  templateUrl: './coverage.html',
  styleUrl: './coverage.css',
})
export class Coverage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  readonly context = inject(ProgramContext);

  readonly programId = input.required<string>();
  /** `?jurisdiction=CA`: the selected state lives in the URL, so a link or a reload opens it. */
  readonly jurisdictionParam = input<string | undefined>(undefined, { alias: 'jurisdiction' });

  readonly flags = FLAGS;
  /** Matrices by state code for the program in `loadedFor`. */
  readonly matrices = signal<ReadonlyMap<string, CoverageMatrix>>(new Map());
  readonly errors = signal<ReadonlyMap<string, string>>(new Map());
  readonly filter = signal<Flag | null>(null);
  /** The cell under the pointer or the keyboard: its row and column header are highlighted. */
  readonly hover = signal<Position | null>(null);
  /** The roving tab stop among the cells. */
  readonly stop = signal<Position | null>(null);
  /** The cell whose card is open. */
  readonly previewed = signal<{ row: Row; cell: Cell } | null>(null);

  private loadedFor = '';
  private readonly requested = new Set<string>();
  private readonly card = viewChild<Popover>('card');
  private readonly cardAction = viewChild<ElementRef<HTMLAnchorElement>>('cardAction');
  private focusCardOnShow = false;
  /** Escape in the card gives the focus back to its cell, which must not open the card again. */
  private returning = false;
  private showTimer = 0;
  private hideTimer = 0;

  readonly program = computed(() => {
    const p = this.context.program();
    return p && p.id === this.programId() ? p : null;
  });
  readonly codes = computed(() => this.program()?.jurisdictions ?? []);
  readonly jurisdiction = computed(() => {
    const wanted = this.jurisdictionParam();
    const codes = this.codes();
    return wanted && codes.includes(wanted) ? wanted : (codes[0] ?? '');
  });
  readonly pills = computed<StatePill[]>(() => this.codes().map((code) => {
    const m = this.matrices().get(code);
    return { code, name: this.context.jurisdictionName(code), progress: m && m.requirements.length > 0 ? unflaggedShare(m) : null };
  }));
  readonly matrix = computed(() => this.matrices().get(this.jurisdiction()) ?? null);
  readonly error = computed(() => this.errors().get(this.jurisdiction()) ?? null);
  readonly view = computed(() => {
    const m = this.matrix();
    return m ? coverageView(m) : null;
  });
  readonly chips = computed(() => flagCounts(this.view()?.rows ?? []).map(({ flag, count }) => ({ flag, count, ...FLAGS[flag], name: `${FLAGS[flag].label}, ${count}` })));
  readonly tabStop = computed(() => {
    const view = this.view();
    const at = this.stop();
    return view && at && view.rows[at.row]?.cells[at.col] ? at : view ? firstCell(view) : null;
  });
  /** The polite live region: what the chosen flag highlights, in words. */
  readonly message = computed(() => {
    const flag = this.filter();
    const rows = this.view()?.rows ?? [];
    if (!flag) { return ''; }
    const count = rows.filter((r) => r.flag === flag).length;
    return `${FLAGS[flag].label}: ${count} of ${rows.length} requirements highlighted.`;
  });

  constructor() {
    registerComponentTokens(COVERAGE_TOKENS);
    effect(() => {
      const id = this.programId();
      untracked(() => this.context.ensure(id));
    });
    // The selected state first, then every other target state once, for the rings on the pills.
    effect(() => {
      const program = this.program();
      const code = this.jurisdiction();
      if (!program || !code) { return; }
      untracked(() => {
        if (this.loadedFor !== program.id) {
          this.loadedFor = program.id;
          this.requested.clear();
          this.matrices.set(new Map());
          this.errors.set(new Map());
        }
        this.load(program.id, code).finally(() => program.jurisdictions.forEach((other) => this.load(program.id, other)));
      });
    });
    // A new state starts unfiltered, with the keyboard on its first cell and no card open.
    effect(() => {
      this.jurisdiction();
      untracked(() => {
        this.filter.set(null);
        this.stop.set(null);
        this.hover.set(null);
        this.closeCard();
      });
    });
    inject(DestroyRef).onDestroy(() => { clearTimeout(this.showTimer); clearTimeout(this.hideTimer); });
  }

  select(code: string | number | undefined): void {
    if (typeof code === 'string' && code !== this.jurisdiction()) {
      this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    }
  }

  private async load(programId: string, code: string): Promise<void> {
    if (this.requested.has(code)) { return; }
    this.requested.add(code);
    try {
      const matrix = await firstValueFrom(this.api.coverage(programId, code));
      if (this.loadedFor === programId) { this.matrices.update((all) => new Map(all).set(code, matrix)); }
    } catch (e: unknown) {
      this.requested.delete(code);
      if (this.loadedFor === programId) {
        this.errors.update((all) => new Map(all).set(code, problemDetail(e, `The coverage for ${this.context.jurisdictionName(code)} could not be loaded.`)));
      }
    }
  }

  dimmed(row: Row): boolean {
    return !matchesFlag(row, this.filter());
  }

  /** Column by column: the requirement column first, each course after it, the flag column with the last. */
  delay(col: number): number {
    return Math.min(col, STAGGER_CAP);
  }

  isStop(row: number, col: number): boolean {
    const at = this.tabStop();
    return at !== null && at.row === row && at.col === col;
  }

  onKey(event: KeyboardEvent): void {
    const view = this.view();
    const from = this.tabStop();
    if (!view || !from) { return; }
    if (event.key === 'Escape') {
      this.closeCard();
      return;
    }
    if (event.key === 'Enter') {
      const at = this.positionOf(event.target);
      if (at) {
        event.preventDefault();
        this.openCard(at, event.target as HTMLElement, 0, true);
      }
      return;
    }
    const key = (event.ctrlKey || event.metaKey) && (event.key === 'Home' || event.key === 'End') ? (event.key === 'Home' ? 'FirstCell' : 'LastCell') : MOVES[event.key];
    if (!key) { return; }
    event.preventDefault();
    const to = move(view, this.positionOf(event.target) ?? from, key);
    this.stop.set(to);
    this.host.querySelector<HTMLButtonElement>(`button[data-pos="${to.row}-${to.col}"]`)?.focus();
  }

  onFocus(row: number, col: number, el: HTMLElement): void {
    this.stop.set({ row, col });
    if (this.returning) {
      this.returning = false;
      return;
    }
    this.openCard({ row, col }, el, 0, false);
  }

  onEnter(row: number, col: number, el: HTMLElement): void {
    this.openCard({ row, col }, el, 120, false);
  }

  onLeave(): void {
    this.hover.set(null);
    this.scheduleClose();
  }

  /** Leaving a cell or the card closes it shortly after, so the pointer can travel from one to the other. */
  scheduleClose(): void {
    clearTimeout(this.showTimer);
    clearTimeout(this.hideTimer);
    this.hideTimer = window.setTimeout(() => {
      if (!this.cardHasFocus()) { this.closeCard(); }
    }, 160);
  }

  keepOpen(): void {
    clearTimeout(this.hideTimer);
  }

  closeCard(): void {
    clearTimeout(this.showTimer);
    clearTimeout(this.hideTimer);
    this.card()?.hide();
  }

  /** Escape inside the card closes it and gives the focus back to its cell. */
  onCardKey(event: KeyboardEvent): void {
    if (event.key !== 'Escape') { return; }
    const target = this.card()?.target as HTMLElement | null | undefined;
    this.closeCard();
    if (target) {
      this.returning = true;
      target.focus();
    }
  }

  onCardShown(): void {
    if (this.focusCardOnShow) {
      this.focusCardOnShow = false;
      this.cardAction()?.nativeElement.focus();
    }
  }

  private cardHasFocus(): boolean {
    const container = this.card()?.container as HTMLElement | null | undefined;
    return !!container && container.contains(document.activeElement);
  }

  private positionOf(target: EventTarget | null): Position | null {
    const pos = (target as HTMLElement | null)?.dataset?.['pos'];
    if (!pos) { return null; }
    const [row, col] = pos.split('-').map(Number);
    return { row, col };
  }

  private openCard(at: Position, el: HTMLElement, delay: number, focusAction: boolean): void {
    const card = this.card();
    const row = this.view()?.rows[at.row];
    const cell = row?.cells[at.col];
    if (!card || !row || !cell) { return; }
    this.hover.set(at);
    clearTimeout(this.hideTimer);
    clearTimeout(this.showTimer);
    this.showTimer = window.setTimeout(() => {
      this.previewed.set({ row, cell });
      if (card.overlayVisible()) {
        card.target = el;
        card.align();
        if (focusAction) { requestAnimationFrame(() => this.cardAction()?.nativeElement.focus()); }
      } else {
        this.focusCardOnShow = focusAction;
        card.show(null, el);
      }
    }, delay);
  }
}
