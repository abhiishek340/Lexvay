/** Items of one local calendar day, under the heading a list shows for it. */
export interface DayGroup<T> { readonly key: string; readonly label: string; readonly items: T[] }

/** "2026-11-27" for the local calendar day of an instant. */
function dayKey(date: Date): string {
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * Items grouped by the local calendar day of their instant, in the order given (newest first wherever a list reads from
 * the latest down). Days read "Today", "Yesterday", then "Wednesday 25 November", with the year only when it is not the
 * current one. The audit trail and Changes share it (DESIGN.md "Changes, Audit trail").
 */
export function groupByDay<T>(items: readonly T[], at: (item: T) => string, now: Date): DayGroup<T>[] {
  const today = dayKey(now);
  const yesterday = dayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1));
  const groups: { key: string; label: string; items: T[] }[] = [];
  for (const item of items) {
    const instant = new Date(at(item));
    const key = dayKey(instant);
    let group = groups.at(-1);
    if (group?.key !== key) {
      const sameYear = instant.getFullYear() === now.getFullYear();
      const label = key === today ? 'Today' : key === yesterday ? 'Yesterday'
        : instant.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', ...(sameYear ? {} : { year: 'numeric' }) }).replace(',', '');
      group = { key, label, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
}
