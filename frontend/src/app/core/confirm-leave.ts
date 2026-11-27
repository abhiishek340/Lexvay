import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

/** A screen that may hold unsaved edits decides whether a navigation away from it (or within it) may proceed. */
export interface ConfirmsLeave { confirmLeave(nextUrl: string): boolean | Promise<boolean> }

export function confirmLeave(screen: ConfirmsLeave, _route: ActivatedRouteSnapshot, _state: RouterStateSnapshot, next: RouterStateSnapshot): boolean | Promise<boolean> {
  return screen.confirmLeave(next.url);
}
