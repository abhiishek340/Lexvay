import { Component, DestroyRef, ElementRef, afterRenderEffect, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Popover } from 'primeng/popover';
import { Skeleton } from 'primeng/skeleton';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tooltip } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CoverageMatrix } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { ProgramContext } from '../../core/program-context';
import { registerComponentTokens } from '../../theme/component-tokens';
import { Help, HelpTerm } from '../../ui/help';
import { MarkShapeIcon } from '../../ui/mark-shape';
import { StateTab, StateTabs } from '../../ui/state-tabs';
import { COVERAGE_TOKENS } from './coverage-theme';
import { CELL_SHAPE, Cell, FLAGS, FLAG_ORDER, Flag, MoveKey, Position, Row, coverageView, firstCell, flagCounts, matchesFlag, move, unflaggedShare } from './coverage-view';

const HELP: readonly HelpTerm[] = [
  { term: 'Filled circle', definition: "A quote the system verified in the course's documents." },
  { term: 'Outline circle', definition: 'A passage a person provided.' },
  { term: 'Ring with a dot', definition: 'Both: a verified quote and a passage a person provided.' },
  { term: 'Dashed circle', definition: 'A quote on an older version of the course.' },
  { term: 'Flags', definition: 'Say where the evidence sits, never whether it is enough. People decide that in Review.' },
];

/** The filter tab that highlights no flag. */
const ALL = 'ALL';

const MOVES: Record<string, MoveKey> = { ArrowLeft: 'ArrowLeft', ArrowRight: 'ArrowRight', ArrowUp: 'ArrowUp', ArrowDown: 'ArrowDown', Home: 'Home', End: 'End' };
/** Space left between a revealed cell and the sticky column it was hidden under. */
const REVEAL_MARGIN = 8;

/**
 * Coverage (F10b, DESIGN.md "Coverage"): for one target state, which course carries the evidence of each requirement,
 * and where a requirement rests on an elective, on one course, on outdated evidence or on nothing. The matrix is a table
 * on surface whose cells are status-mark shapes; flags are words with a mark; a text tab row highlights one flag. It
 * reports the evidence and the flags the server computed (D-392) and concludes nothing (product rule 1). One request per
 * state: the state in the URL first, then the others for their tab counts; each answer is kept while the screen is open.
 */
@Component({
  selector: 'lx-coverage',
  imports: [RouterLink, ButtonDirective, ButtonLabel, Message, Popover, Skeleton, Tab, TabList, TabPanel, TabPanels, Tabs, Tooltip, Help, MarkShapeIcon, StateTabs],
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
  readonly cellShape = CELL_SHAPE;
  readonly help = HELP;
  readonly all = ALL;
  /** Every filter tab's value, for the panels: All, then each flag. */
  readonly filterValues: readonly string[] = [ALL, ...FLAG_ORDER];
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
  /** Refs whose title the two-line clamp cuts (narrow windows): only those show the full title as a tooltip. */
  readonly clipped = signal<ReadonlySet<string>>(new Set());

  private loadedFor = '';
  private readonly requested = new Set<string>();
  private readonly card = viewChild<Popover>('card');
  private readonly cardAction = viewChild<ElementRef<HTMLAnchorElement>>('cardAction');
  private focusCardOnShow = false;
  /** Escape in the card gives the focus back to its cell, which must not open the card again. */
  private returning = false;
  private showTimer = 0;
  /** Bumped by every open and close, so a card scheduled for a later frame never opens after it was superseded. */
  private showToken = 0;
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
  readonly tabs = computed<StateTab[]>(() => this.codes().map((code) => {
    const m = this.matrices().get(code);
    return { code, name: this.context.jurisdictionName(code), progress: m && m.requirements.length > 0 ? unflaggedShare(m) : null };
  }));
  readonly matrix = computed(() => this.matrices().get(this.jurisdiction()) ?? null);
  readonly error = computed(() => this.errors().get(this.jurisdiction()) ?? null);
  readonly view = computed(() => {
    const m = this.matrix();
    return m ? coverageView(m) : null;
  });
  /** The filter tabs after All: each flag some row carries, with its count. */
  readonly flagTabs = computed(() => flagCounts(this.view()?.rows ?? []).map(({ flag, count }) => ({ flag, count, ...FLAGS[flag], name: `${FLAGS[flag].label}, ${count}` })));
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
    // The selected state first, then every other target state once, for the counts on their tabs.
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
    // Whether a title is cut depends on the rendered rows and the window width: measure after each render and resize.
    afterRenderEffect(() => {
      this.view();
      this.measureTitles();
    });
    const resize = typeof ResizeObserver === 'function' ? new ResizeObserver(() => this.measureTitles()) : null;
    resize?.observe(this.host);
    inject(DestroyRef).onDestroy(() => { clearTimeout(this.showTimer); clearTimeout(this.hideTimer); resize?.disconnect(); });
  }

  private measureTitles(): void {
    const cut = new Set<string>();
    this.host.querySelectorAll<HTMLElement>('.lx-req-title').forEach((title) => {
      if (title.scrollHeight > title.clientHeight + 1 && title.dataset['ref']) { cut.add(title.dataset['ref']); }
    });
    const now = untracked(this.clipped);
    if (cut.size !== now.size || [...cut].some((ref) => !now.has(ref))) { this.clipped.set(cut); }
  }

  select(code: string | number | undefined): void {
    if (typeof code === 'string' && code !== this.jurisdiction()) {
      this.router.navigate([], { relativeTo: this.route, queryParams: { jurisdiction: code }, replaceUrl: true });
    }
  }

  private async load(programId: string, code: string): Promise<void> {
    if (this.requested.has(code)) { return; }
    this.requested.add(code);
    if (this.errors().has(code)) {
      // Asked again after a failure: the skeleton replaces the old message while the request runs.
      this.errors.update((all) => { const next = new Map(all); next.delete(code); return next; });
    }
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

  /** A filter tab was chosen: All highlights nothing, a flag dims the rows without it. The matrix is drawn anew, so no card stays open. */
  setFilter(value: string | number | undefined): void {
    const flag = FLAG_ORDER.find((f) => f === value) ?? null;
    if (flag !== this.filter() && (flag !== null || value === ALL)) {
      this.closeCard();
      this.hover.set(null);
      this.previewed.set(null);
      this.filter.set(flag);
    }
  }

  dimmed(row: Row): boolean {
    return !matchesFlag(row, this.filter());
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
    this.reveal(el);
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
    this.showToken++;
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
    this.showToken++;
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

  /** Scrolls the frame so a focused cell sits clear of the sticky requirement and flag columns, never under them. */
  private reveal(cell: HTMLElement): void {
    const frame = cell.closest<HTMLElement>('.lx-frame');
    const row = cell.closest('tr');
    const left = row?.querySelector('.lx-req-col')?.getBoundingClientRect().right;
    const right = row?.querySelector('.lx-flag-col')?.getBoundingClientRect().left;
    if (!frame || left === undefined || right === undefined) { return; }
    const box = cell.getBoundingClientRect();
    if (box.left < left) {
      frame.scrollLeft -= left - box.left + REVEAL_MARGIN;
    } else if (box.right > right) {
      frame.scrollLeft += box.right - right + REVEAL_MARGIN;
    }
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
    const token = ++this.showToken;
    // In the next frame: a focus that scrolled the frame fires its scroll event first, which closes an open Popover.
    this.showTimer = window.setTimeout(() => requestAnimationFrame(() => {
      if (token !== this.showToken) { return; }
      this.previewed.set({ row, cell });
      if (card.overlayVisible()) {
        card.target = el;
        card.align();
        if (focusAction) { requestAnimationFrame(() => this.cardAction()?.nativeElement.focus()); }
      } else {
        this.focusCardOnShow = focusAction;
        card.show(null, el);
      }
    }), delay);
  }
}
