import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { beforeSignIn } from './navigation';
import { ProgramContext } from './program-context';
import { SessionService } from './session.service';

/** The problem detail of the 401 that ends a session whose role, access or password changed (F5a, D-374). */
export const ACCESS_CHANGED = 'Your access changed. Sign in again.';

/** True for the 401 the server answers once a session no longer matches its account; other 401s (a wrong password) are not. */
export function accessChanged(e: unknown): boolean {
  return e instanceof HttpErrorResponse && e.status === 401 && (e.error as { detail?: unknown } | null)?.detail === ACCESS_CHANGED;
}

/**
 * Any request answered with that 401 signs the person out here as well: the session is forgotten, the program context
 * cleared, and sign-in opens with the sentence (`SessionService.notice`). The error still reaches the caller, which
 * stops what it was doing.
 */
export const accessInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionService);
  const context = inject(ProgramContext);
  const router = inject(Router);
  return next(request).pipe(catchError((e: unknown) => {
    if (accessChanged(e)) {
      session.end(ACCESS_CHANGED);
      context.clear();
      // Choosing a password stays where it is: the person there is about to sign in anyway.
      if (!beforeSignIn(router.url)) { void router.navigateByUrl('/login'); }
    }
    return throwError(() => e);
  }));
};
