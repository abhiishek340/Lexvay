import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Audit } from './screens/audit/audit';
import { Changes } from './screens/changes/changes';
import { Home } from './screens/home';
import { Login } from './screens/login/login';
import { Overview } from './screens/overview/overview';
import { Report } from './screens/report/report';
import { Review } from './screens/review/review';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Sign in · Lexvay' },
  { path: '', component: Home, canActivate: [authGuard], pathMatch: 'full', title: 'Lexvay' },
  { path: 'programs/:programId/overview', component: Overview, canActivate: [authGuard], title: 'Program overview · Lexvay' },
  { path: 'programs/:programId/review', component: Review, canActivate: [authGuard], title: 'Review · Lexvay' },
  { path: 'programs/:programId/changes', component: Changes, canActivate: [authGuard], title: 'Changes · Lexvay' },
  { path: 'programs/:programId/report', component: Report, canActivate: [authGuard], title: 'Evidence report · Lexvay' },
  { path: 'programs/:programId/audit', component: Audit, canActivate: [authGuard], title: 'Audit trail · Lexvay' },
  { path: '**', redirectTo: '' },
];
