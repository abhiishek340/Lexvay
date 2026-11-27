import { Routes } from '@angular/router';
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
      { path: 'changes', loadComponent: () => import('./screens/changes/changes').then((m) => m.Changes), title: 'Changes · Lexvay' },
      { path: 'report', loadComponent: () => import('./screens/report/report').then((m) => m.Report), title: 'Evidence report · Lexvay' },
      { path: 'audit', loadComponent: () => import('./screens/audit/audit').then((m) => m.Audit), title: 'Audit trail · Lexvay' },
      // Test harness for the end-to-end suite. ngDevMode is false in production builds, so the route and its chunk are dropped there.
      ...(ngDevMode
        ? [{ path: 'dev/document-viewer', loadComponent: () => import('./dev/document-viewer-harness').then((m) => m.DocumentViewerHarness), title: 'Document viewer harness · Lexvay' }]
        : []),
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
    ],
  },
  { path: '**', redirectTo: '' },
];
