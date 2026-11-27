import { Params } from '@angular/router';
import { US_TILES, UsTile } from './us-tiles';

/**
 * Where a target state stands on the map: a current signed determination's outcome, or in review. A state that is
 * not a target of the program is simply absent from the map's input and drawn quietly.
 */
export type TileStatus = 'MEETS' | 'DOES_NOT_MEET' | 'NOT_DETERMINED' | 'IN_REVIEW';

/** The one thing a tile leads to: its popover's button, and Enter or a click on the tile itself. */
export interface MapAction { readonly label: string; readonly commands: readonly unknown[]; readonly queryParams?: Params }

/** A target state as the map shows it. `status` is the words shown beside the icon ("Ready to sign"). */
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
  const progress = state.total > 0 ? `${state.reviewed} of ${state.total} reviewed` : 'not assessed';
  return `${name}, ${progress}, ${state.status.charAt(0).toLowerCase()}${state.status.slice(1)}`;
}

/** The share of a target's requirements with a decision, from 0 to 1 (the tile's bottom-up fill). */
export function reviewedShare(state: MapState): number {
  return state.total > 0 ? Math.min(1, state.reviewed / state.total) : 0;
}

export type MapKey = 'ArrowLeft' | 'ArrowRight' | 'ArrowUp' | 'ArrowDown' | 'Home' | 'End';

/**
 * Keyboard movement between the target tiles (roving tabindex). Home and End go to the first and last target in
 * reading order. An arrow goes to the nearest target in that direction: the smallest distance along the arrow's
 * axis plus twice the distance across it, so the same row or column wins over a closer diagonal. With no target
 * in that direction the focus stays.
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
    if (t === from || ahead <= 0) { continue; }
    const score = ahead + 2 * Math.abs(t[across] - from[across]);
    if (score < bestScore) {
      best = t;
      bestScore = score;
    }
  }
  return best;
}
