import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { authGuard, landingGuard } from './core/auth.guard';

/**
 * Each screen is its own lazy chunk; the shell, session and theme are the only eager code. A screen that shows icons
 * beyond SHELL_ICONS is loaded with `loadChildren` from its `<screen>.routes.ts`, whose route provides them: route
 * providers are built at start-up, so a route that named them here would pull them into the initial bundle.
 */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./screens/login/login').then((m) => m.Login), title: 'Sign in · Lexvay' },
  { path: '', loadComponent: () => import('./screens/home').then((m) => m.Home), canActivate: [authGuard, landingGuard], pathMatch: 'full', title: 'Programs · Lexvay' },
  {
    path: 'programs/:programId',
    canActivate: [authGuard],
    children: [
      { path: 'overview', loadChildren: () => import('./screens/overview/overview.routes') },
      { path: 'review', loadChildren: () => import('./screens/review/review.routes') },
      { path: 'coverage', loadChildren: () => import('./screens/coverage/coverage.routes') },
      { path: 'requests', loadChildren: () => import('./screens/requests/requests.routes') },
      { path: 'changes', loadChildren: () => import('./screens/changes/changes.routes') },
      { matcher: rulesMatcher, loadChildren: () => import('./screens/rules/rules.routes') },
      { path: 'report', loadChildren: () => import('./screens/report/report.routes') },
      { path: 'audit', loadChildren: () => import('./screens/audit/audit.routes') },
      // Test harness for the end-to-end suite. ngDevMode is false in production builds, so the route and its chunk are dropped there.
      ...(ngDevMode
        ? [{ path: 'dev/document-viewer', loadComponent: () => import('./dev/document-viewer-harness').then((m) => m.DocumentViewerHarness), title: 'Document viewer harness · Lexvay' }]
        : []),
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
    ],
  },
  { path: 'register', canActivate: [authGuard], loadChildren: () => import('./screens/register/register.routes') },
  // The shared v2 components at every size, for screenshot review; development builds only, like the viewer harness.
  ...(ngDevMode
    ? [{ path: 'dev/ui', canActivate: [authGuard], loadComponent: () => import('./dev/ui-harness').then((m) => m.UiHarness), title: 'Components · Lexvay' }]
    : []),
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
