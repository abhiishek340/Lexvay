import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { confirmLeave } from '../../core/confirm-leave';
import { RULES_ICONS, Rules } from './rules';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Rules, providers: [provideIcons(RULES_ICONS)], canDeactivate: [confirmLeave], title: 'Rules · Lexvay' }] satisfies Routes;
