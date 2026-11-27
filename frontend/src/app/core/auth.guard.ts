import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
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
 * Screens only administrators use (People), after authGuard. Anyone else goes to the overview of the program last
 * worked on, or to Programs, which opens the only one; the server refuses them in any case.
 */
export const adminGuard: CanActivateFn = () => {
  const program = inject(ProgramContext).program();
  return inject(SessionService).isAdmin() ? true : inject(Router).createUrlTree(program ? ['/programs', program.id, 'overview'] : ['/']);
};
