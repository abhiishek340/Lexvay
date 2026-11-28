import { describe, expect, it } from 'vitest';
import { groupByDay } from './days';

/** A local time on a day of November 2026, as the ISO instant the server sends. */
const at = (day: number, hour: number): string => new Date(2026, 10, day, hour, 0).toISOString();
const items = [{ id: '1', at: at(27, 11) }, { id: '2', at: at(27, 10) }, { id: '3', at: at(26, 9) }, { id: '4', at: at(20, 8) }];
const instant = (i: { at: string }): string => i.at;

describe('groupByDay', () => {
  it('groups by local day in the order given, naming today and yesterday', () => {
    const groups = groupByDay(items, instant, new Date(2026, 10, 27, 15, 0));
    expect(groups.map((g) => [g.key, g.label, g.items.map((i) => i.id)])).toEqual([
      ['2026-11-27', 'Today', ['1', '2']],
      ['2026-11-26', 'Yesterday', ['3']],
      ['2026-11-20', 'Friday 20 November', ['4']],
    ]);
  });

  it('adds the year to a day of another year, and gives nothing for nothing', () => {
    const [group] = groupByDay(items.slice(3), instant, new Date(2027, 0, 2, 9, 0));
    expect(group.label).toBe('Friday 20 November 2026');
    expect(groupByDay([], instant, new Date())).toEqual([]);
  });
});
