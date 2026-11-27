import { Params } from '@angular/router';
import { OUTCOME_LABEL } from '../../core/labels';
import { US_TILES, UsTile } from './us-tiles';

/**
 * Where a target state stands on the map: a current signed determination's outcome, in review, or (on a map of
 * several programs, the register) signed with different outcomes. A state that is not a target is simply absent
 * from the map's input and drawn quietly.
 */
export type TileStatus = 'MEETS' | 'DOES_NOT_MEET' | 'NOT_DETERMINED' | 'MIXED' | 'IN_REVIEW';

/** The one thing a tile leads to: its popover's button, and Enter or a click on the tile itself. */
export interface MapAction { readonly label: string; readonly commands: readonly unknown[]; readonly queryParams?: Params }

/**
 * A target state as the map shows it. `status` is the words shown beside the icon ("Ready to sign"); `reviewed` of
 * `total` is the review progress, and a total of 0 means there is no progress to show (not assessed, or a map of
 * signatures).
 */
export interface MapState {
  readonly code: string;
  readonly tile: TileStatus;
  readonly reviewed: number;
  readonly total: number;
  readonly status: string;
  readonly action: MapAction | null;
}

/** A cell of the grid: every one of the 51 tiles, with its state when it is a target. */
export interface MapCell { readonly tile: UsTile; readonly state: MapState | null }

export function mapCells(states: readonly MapState[]): MapCell[] {
  const byCode = new Map(states.map((s) => [s.code, s]));
  return US_TILES.map((tile) => ({ tile, state: byCode.get(tile.code) ?? null }));
}

/** "California, 8 of 8 reviewed, determined not to meet": the tile's full accessible name. */
export function tileName(name: string, state: MapState): string {
  const status = `${state.status.charAt(0).toLowerCase()}${state.status.slice(1)}`;
  return state.total > 0 ? `${name}, ${state.reviewed} of ${state.total} reviewed, ${status}` : `${name}, ${status}`;
}

/** What a legend entry stands for: a tile status, or a state that is not a target. */
export type LegendKey = TileStatus | 'NONE';
export interface LegendEntry { readonly key: LegendKey; readonly label: string }

const LEGEND: readonly LegendEntry[] = [
  { key: 'MEETS', label: OUTCOME_LABEL.MEETS },
  { key: 'DOES_NOT_MEET', label: OUTCOME_LABEL.DOES_NOT_MEET },
  { key: 'NOT_DETERMINED', label: OUTCOME_LABEL.NOT_DETERMINED },
  { key: 'MIXED', label: 'Different outcomes' },
  { key: 'IN_REVIEW', label: 'In review' },
  { key: 'NONE', label: 'Not a target' },
];

/** The map's one legend: the statuses its tiles show, in a fixed order, and "Not a target" for the quiet tiles. */
export function legendEntries(states: readonly MapState[]): LegendEntry[] {
  const shown = new Set<LegendKey>(states.map((s) => s.tile));
  if (states.length < US_TILES.length) { shown.add('NONE'); }
  return LEGEND.filter((entry) => shown.has(entry.key));
}

/** The share of a target's requirements with a decision, from 0 to 1 (the tile's bottom-up fill). */
export function reviewedShare(state: MapState): number {
  return state.total > 0 ? Math.min(1, state.reviewed / state.total) : 0;
}

export type MapKey = 'ArrowLeft' | 'ArrowRight' | 'ArrowUp' | 'ArrowDown' | 'Home' | 'End';

/**
 * Keyboard movement between the target tiles (roving tabindex). Home and End go to the first and last target in
 * reading order. An arrow goes to the nearest target within 45 degrees of its direction: the smallest distance along
 * the arrow's axis plus twice the distance across it, so the same row or column wins over a closer diagonal. With no
 * target in that direction the focus stays.
 */
export function nextTarget(targets: readonly UsTile[], from: UsTile, key: MapKey): UsTile {
  const ordered = [...targets].sort((a, b) => a.row - b.row || a.col - b.col);
  if (key === 'Home') { return ordered[0] ?? from; }
  if (key === 'End') { return ordered[ordered.length - 1] ?? from; }
  const [along, across] = key === 'ArrowLeft' || key === 'ArrowRight' ? (['col', 'row'] as const) : (['row', 'col'] as const);
  const sign = key === 'ArrowRight' || key === 'ArrowDown' ? 1 : -1;
  let best: UsTile = from;
  let bestScore = Infinity;
  for (const t of ordered) {
    const ahead = (t[along] - from[along]) * sign;
    const aside = Math.abs(t[across] - from[across]);
    if (t === from || ahead <= 0 || aside > ahead) { continue; }
    const score = ahead + 2 * aside;
    if (score < bestScore) {
      best = t;
      bestScore = score;
    }
  }
  return best;
}
