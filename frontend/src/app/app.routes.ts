import { ActivatedRouteSnapshot, RouterStateSnapshot, Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { authGuard } from './core/auth.guard';

/** Each screen is its own lazy chunk; the shell, session and theme are the only eager code. */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./screens/login/login').then((m) => m.Login), title: 'Sign in · Lexvay' },
  { path: '', loadComponent: () => import('./screens/home').then((m) => m.Home), canActivate: [authGuard], pathMatch: 'full', title: 'Programs · Lexvay' },
  {
    path: 'programs/:programId',
    canActivate: [authGuard],
    children: [
      { path: 'overview', loadComponent: () => import('./screens/overview/overview').then((m) => m.Overview), title: 'Program overview · Lexvay' },
      { path: 'review', loadComponent: () => import('./screens/review/review').then((m) => m.Review), title: 'Review · Lexvay' },
      { path: 'requests', loadComponent: () => import('./screens/requests/requests').then((m) => m.Requests), title: 'Evidence requests · Lexvay' },
      { path: 'changes', loadComponent: () => import('./screens/changes/changes').then((m) => m.Changes), title: 'Changes · Lexvay' },
      { matcher: rulesMatcher, loadComponent: () => import('./screens/rules/rules').then((m) => m.Rules), canDeactivate: [confirmLeave], title: 'Rules · Lexvay' },
      { path: 'report', loadComponent: () => import('./screens/report/report').then((m) => m.Report), title: 'Evidence report · Lexvay' },
      { path: 'audit', loadComponent: () => import('./screens/audit/audit').then((m) => m.Audit), title: 'Audit trail · Lexvay' },
      // Test harness for the end-to-end suite. ngDevMode is false in production builds, so the route and its chunk are dropped there.
      ...(ngDevMode
        ? [{ path: 'dev/document-viewer', loadComponent: () => import('./dev/document-viewer-harness').then((m) => m.DocumentViewerHarness), title: 'Document viewer harness · Lexvay' }]
        : []),
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
    ],
  },
  { path: 'register', loadComponent: () => import('./screens/register/register').then((m) => m.Register), canActivate: [authGuard], title: 'Determination register · Lexvay' },
  { path: '**', redirectTo: '' },
];

/**
 * "rules" and "rules/:ruleVersionId" as one route, so choosing a version in the list keeps the same screen
 * instance (and the keyboard focus in the list) instead of building a new one.
 */
export function rulesMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments[0]?.path !== 'rules' || segments.length > 2) { return null; }
  return { consumed: segments, posParams: segments[1] ? { ruleVersionId: segments[1] } : {} };
}

/** A screen that may hold unsaved edits decides whether a navigation away from it (or within it) may proceed. */
export interface ConfirmsLeave { confirmLeave(nextUrl: string): boolean | Promise<boolean> }

export function confirmLeave(screen: ConfirmsLeave, _route: ActivatedRouteSnapshot, _state: RouterStateSnapshot, next: RouterStateSnapshot): boolean | Promise<boolean> {
  return screen.confirmLeave(next.url);
}
