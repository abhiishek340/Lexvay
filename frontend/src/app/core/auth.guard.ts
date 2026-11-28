import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { ProgramContext } from './program-context';
import { SessionService } from './session.service';

export const authGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);
  if (!session.loaded()) {
    await session.load();
  }
  return session.authenticated() ? true : router.createUrlTree(['/login']);
};

/**
 * "/" lands on the overview of the only program, inside the same navigation, so signing in is one route change and one
 * view transition. With several programs (or none, or a failed request) it stays and the screen lists them.
 */
export const landingGuard: CanActivateFn = async () => {
  const api = inject(ApiService);
  const router = inject(Router);
  const programs = await firstValueFrom(api.programs()).catch(() => []);
  return programs.length === 1 ? router.createUrlTree(['/programs', programs[0].id, 'overview']) : true;
};

/**
 * Screens only administrators use (People), after authGuard. Anyone else goes to the overview of the program last
 * worked on, or to Programs, which opens the only one; the server refuses them in any case.
 */
export const adminGuard: CanActivateFn = () => {
  const program = inject(ProgramContext).program();
  return inject(SessionService).isAdmin() ? true : inject(Router).createUrlTree(program ? ['/programs', program.id, 'overview'] : ['/']);
};
