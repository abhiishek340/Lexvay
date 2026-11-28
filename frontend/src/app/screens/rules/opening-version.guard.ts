import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ProgramContext } from '../../core/program-context';
import { openingVersion } from '../../core/rules';

/**
 * Rules never starts on an empty detail: when the address names no version, it is replaced by the one that waits for a
 * person (or the newest approved one), inside the same navigation, so opening Rules is one route change and one view
 * transition and Back never returns to the bare address. The query (?new=1) travels along. A tenant without versions,
 * or a program that failed to load, stays on the bare address and the screen says so.
 */
export const openingVersionGuard: CanActivateFn = async (route) => {
  const context = inject(ProgramContext);
  const router = inject(Router);
  const { programId, ruleVersionId } = route.params;
  if (ruleVersionId) { return true; }
  await context.ensure(programId);
  const id = openingVersion(context.ruleVersionGroups());
  return id ? router.createUrlTree(['/programs', programId, 'rules', id], { queryParams: route.queryParams }) : true;
};
