/**
 * "Dana Whitfield" is "DW", "Dr. Priya Raman" is "PR": the first letters of the first and last names. Its own module,
 * so the shell's avatar uses it without pulling the Seal component into the initial bundle.
 */
export function initials(name: string): string {
  const words = name.replace(/^(dr|mr|mrs|ms|prof)\.?\s+/i, '').split(/\s+/).filter(Boolean);
  if (words.length === 0) { return ''; }
  const first = words[0].charAt(0);
  const last = words.length > 1 ? words[words.length - 1].charAt(0) : '';
  return `${first}${last}`.toUpperCase();
}
