import { Injectable, signal } from '@angular/core';

/**
 * Whether the requirement list on the Rules screen has edits that are not saved. Provided by the Rules screen and
 * written by its Requirements tab, so the screen's leave guard and its beforeunload handler can ask before the
 * edits are lost.
 */
@Injectable()
export class UnsavedChanges {
  readonly dirty = signal(false);
}

/**
 * Leaving asks first only when there are unsaved edits and the navigation goes somewhere else: another version,
 * another screen, or anywhere outside the program. A change of query or fragment on the same version does not.
 */
export function leaveNeedsConfirmation(dirty: boolean, currentUrl: string, nextUrl: string): boolean {
  return dirty && pathOf(currentUrl) !== pathOf(nextUrl);
}

function pathOf(url: string): string {
  return url.split(/[?#]/)[0].replace(/\/+$/, '');
}
