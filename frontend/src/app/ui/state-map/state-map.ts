import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { phosphorCheckCircle, phosphorCircleDashed, phosphorMinusCircle, phosphorXCircle } from '@ng-icons/phosphor-icons/regular';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Popover } from 'primeng/popover';
import { ProgressRing } from '../progress-ring';
import { MapCell, MapKey, MapState, TileStatus, mapCells, nextTarget, reviewedShare, tileName } from './state-map-model';
import { UsTile } from './us-tiles';

export type MapSize = 'hero' | 'compact' | 'mini';

const KEYS = new Set<string>(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End']);
/** The map registers the icons of its cards itself, so a screen that shows it names nothing for it. */
export const STATE_MAP_ICONS = { phosphorCheckCircle, phosphorCircleDashed, phosphorMinusCircle, phosphorXCircle };
const STATUS_ICON: Record<TileStatus, keyof typeof STATE_MAP_ICONS> = {
  MEETS: 'phosphorCheckCircle',
  DOES_NOT_MEET: 'phosphorXCircle',
  NOT_DETERMINED: 'phosphorMinusCircle',
  IN_REVIEW: 'phosphorCircleDashed',
};

/**
 * Lexvay's United States tile map (DESIGN.md "StateMap"): all 51 tiles in the standard grid, the program's target
 * states coloured by where they stand, the others quiet.
 *   <lx-state-map [states]="mapStates()" size="hero" label="Target states" />
 * A signed outcome fills the tile (found or missing) or rings it in the seal colour (no determination made); a
 * state in review shows the accent tint with a bottom-up fill for the share reviewed. Target tiles are buttons with
 * full names; arrow keys, Home and End move between them (one tab stop); hovering or focusing one opens a card with
 * its ring, status and action, and Enter or a click runs the action. Tiles wave in from west to east on first view.
 * `interactive` false draws a picture only (the sign-in story): no buttons, hidden from assistive technology.
 * Sizes: hero (44 px tiles, shrinking to fit), compact (34 px), mini (16 px, no codes).
 */
@Component({
  selector: 'lx-state-map',
  imports: [RouterLink, NgIcon, ButtonDirective, ButtonLabel, Popover, ProgressRing],
  templateUrl: './state-map.html',
  styleUrl: './state-map.css',
  viewProviders: [provideIcons(STATE_MAP_ICONS)],
  host: { '[attr.data-size]': 'size()', '[class.lx-map-wave]': 'wave()' },
})
export class StateMap {
  readonly states = input<readonly MapState[]>([]);
  readonly size = input<MapSize>('hero');
  readonly interactive = input(true);
  /** The wave entrance on first view; off for a map whose data replaces an earlier map in place. */
  readonly wave = input(true);
  readonly label = input('States');

  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly card = viewChild<Popover>('card');

  readonly cells = computed<MapCell[]>(() => mapCells(this.states()));
  private readonly targets = computed<UsTile[]>(() => this.cells().filter((c) => c.state).map((c) => c.tile));
  /** The roving tab stop: the focused target, else the first one in reading order. */
  private readonly focused = signal<string | null>(null);
  readonly tabStop = computed(() => {
    const code = this.focused();
    return code && this.targets().some((t) => t.code === code) ? code : (this.targets()[0]?.code ?? null);
  });
  /** The state whose card is open. */
  readonly previewed = signal<{ name: string; state: MapState } | null>(null);
  readonly statusIcon = STATUS_ICON;

  private showTimer = 0;
  private hideTimer = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => { clearTimeout(this.showTimer); clearTimeout(this.hideTimer); });
  }

  name(cell: MapCell): string {
    return cell.state ? tileName(cell.tile.name, cell.state) : cell.tile.name;
  }

  share(state: MapState | null): number {
    return state ? reviewedShare(state) : 0;
  }

  onKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.closeCard();
      return;
    }
    if (!KEYS.has(event.key)) { return; }
    const from = this.targets().find((t) => t.code === this.tabStop());
    if (!from) { return; }
    event.preventDefault();
    const to = nextTarget(this.targets(), from, event.key as MapKey);
    this.focused.set(to.code);
    this.host.querySelector<HTMLButtonElement>(`button[data-code="${to.code}"]`)?.focus();
  }

  onFocus(cell: MapCell, el: HTMLElement): void {
    this.focused.set(cell.tile.code);
    this.openCard(cell, el, 0);
  }

  onEnter(cell: MapCell, el: HTMLElement): void {
    this.openCard(cell, el, 120);
  }

  activate(state: MapState): void {
    if (state.action) {
      this.closeCard();
      this.router.navigate([...state.action.commands], { queryParams: state.action.queryParams });
    }
  }

  /** Leaving a tile or the card closes it shortly after, so the pointer can travel from one to the other. */
  scheduleClose(): void {
    clearTimeout(this.showTimer);
    clearTimeout(this.hideTimer);
    this.hideTimer = window.setTimeout(() => this.closeCard(), 160);
  }

  keepOpen(): void {
    clearTimeout(this.hideTimer);
  }

  closeCard(): void {
    clearTimeout(this.showTimer);
    clearTimeout(this.hideTimer);
    this.card()?.hide();
  }

  private openCard(cell: MapCell, el: HTMLElement, delay: number): void {
    const card = this.card();
    if (!cell.state || !card || this.size() === 'mini') { return; }
    const state = cell.state;
    clearTimeout(this.hideTimer);
    clearTimeout(this.showTimer);
    this.showTimer = window.setTimeout(() => {
      this.previewed.set({ name: cell.tile.name, state });
      if (card.overlayVisible()) {
        card.target = el;
        card.align();
      } else {
        card.show(null, el);
      }
    }, delay);
  }
}
