/**
 * The command palette's shortcut in words, for the search field's key hint and the keyboard shortcuts list. Kept apart
 * from the commands so the shell can name it without pulling the palette's command list into the initial bundle.
 */
export function paletteShortcut(platform: string): string {
  return /mac|iphone|ipad/i.test(platform) ? 'Cmd K' : 'Ctrl K';
}
