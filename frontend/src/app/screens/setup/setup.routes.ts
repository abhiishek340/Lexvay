import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { SessionService } from '../../core/session.service';
import { SETUP_ICONS, SetupWorkspace } from './setup';

/** Anyone signed in may look at a program's setup; only contributors and administrators may start a new program. */
const mayOpen: CanActivateFn = (route: ActivatedRouteSnapshot) =>
  route.params['programId'] || inject(SessionService).canSetUp() ? true : inject(Router).createUrlTree(['/']);

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{
  path: '',
  component: SetupWorkspace,
  providers: [provideIcons(SETUP_ICONS)],
  canActivate: [mayOpen],
  title: (route: ActivatedRouteSnapshot) => (route.params['programId'] ? 'Program setup · Lexvay' : 'New program · Lexvay'),
}] satisfies Routes;
