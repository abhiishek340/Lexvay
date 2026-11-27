import { describe, expect, it } from 'vitest';
import { MapState, mapCells, nextTarget, reviewedShare, tileName } from './state-map-model';
import { MAP_COLS, MAP_ROWS, US_TILES, usTile } from './us-tiles';

const STATES_AND_DC = ('AL AK AZ AR CA CO CT DE FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC '
  + 'SD TN TX UT VT VA WA WV WI WY DC').split(' ');

const state = (code: string, over: Partial<MapState> = {}): MapState =>
  ({ code, tile: 'IN_REVIEW', reviewed: 3, total: 8, status: 'Not signed', action: null, ...over });
const tiles = (...codes: string[]) => codes.map((c) => usTile(c)!);

describe('the US tile layout', () => {
  it('has every state and DC exactly once', () => {
    expect(US_TILES.map((t) => t.code).sort()).toEqual([...STATES_AND_DC].sort());
  });

  it('puts every tile in its own cell of the 8 by 12 grid', () => {
    const cells = new Set(US_TILES.map((t) => `${t.row},${t.col}`));
    expect(cells.size).toBe(51);
    for (const t of US_TILES) {
      expect(t.row).toBeGreaterThanOrEqual(0);
      expect(t.row).toBeLessThan(MAP_ROWS);
      expect(t.col).toBeGreaterThanOrEqual(0);
      expect(t.col).toBeLessThan(MAP_COLS);
    }
  });

  it('keeps the familiar corners: Alaska top left, Maine top right, Hawaii bottom left, Florida bottom right of Texas', () => {
    expect(usTile('AK')).toMatchObject({ row: 0, col: 0 });
    expect(usTile('ME')).toMatchObject({ row: 0, col: 11 });
    expect(usTile('HI')).toMatchObject({ row: 7, col: 0 });
    expect(usTile('FL')!.col).toBeGreaterThan(usTile('TX')!.col);
    expect(usTile('CA')!.col).toBeLessThan(usTile('NY')!.col);
  });

  it('pairs every tile with its state, or none when it is not a target', () => {
    const cells = mapCells([state('CA'), state('TX')]);
    expect(cells).toHaveLength(51);
    expect(cells.filter((c) => c.state).map((c) => c.tile.code)).toEqual(['CA', 'TX']);
  });
});

describe('tile names and fill', () => {
  it('names the state, the progress and the status', () => {
    expect(tileName('California', state('CA', { reviewed: 8, total: 8, status: 'Determined not to meet', tile: 'DOES_NOT_MEET' })))
      .toBe('California, 8 of 8 reviewed, determined not to meet');
    expect(tileName('Utah', state('UT', { reviewed: 0, total: 0, status: 'Not assessed' }))).toBe('Utah, not assessed, not assessed');
  });

  it('fills by the share reviewed', () => {
    expect(reviewedShare(state('CA', { reviewed: 6, total: 8 }))).toBe(0.75);
    expect(reviewedShare(state('CA', { reviewed: 0, total: 0 }))).toBe(0);
  });
});

describe('keyboard order between target tiles', () => {
  // The seeded program's targets: AZ, CA, CO, TX, UT.
  const targets = tiles('AZ', 'CA', 'CO', 'TX', 'UT');

  it('moves right along the row, then to the nearest target beyond', () => {
    expect(nextTarget(targets, usTile('CA')!, 'ArrowRight').code).toBe('UT');
    expect(nextTarget(targets, usTile('UT')!, 'ArrowRight').code).toBe('CO');
    expect(nextTarget(targets, usTile('CO')!, 'ArrowRight').code).toBe('CO');
  });

  it('moves left, up and down to the nearest target in that direction', () => {
    expect(nextTarget(targets, usTile('CO')!, 'ArrowLeft').code).toBe('UT');
    expect(nextTarget(targets, usTile('AZ')!, 'ArrowUp').code).toBe('UT');
    expect(nextTarget(targets, usTile('CO')!, 'ArrowDown').code).toBe('AZ');
    expect(nextTarget(targets, usTile('AZ')!, 'ArrowDown').code).toBe('TX');
    expect(nextTarget(targets, usTile('TX')!, 'ArrowDown').code).toBe('TX');
  });

  it('goes to the first and last target in reading order with Home and End', () => {
    expect(nextTarget(targets, usTile('TX')!, 'Home').code).toBe('CA');
    expect(nextTarget(targets, usTile('CA')!, 'End').code).toBe('TX');
  });

  it('visits every target from the first with Right and Down alone', () => {
    const seen = new Set<string>();
    let at = nextTarget(targets, usTile('TX')!, 'Home');
    for (let i = 0; i < 10; i++) {
      seen.add(at.code);
      const right = nextTarget(targets, at, 'ArrowRight');
      at = right !== at ? right : nextTarget(targets, at, 'ArrowDown');
    }
    expect([...seen].sort()).toEqual(['AZ', 'CA', 'CO', 'TX', 'UT']);
  });
});
