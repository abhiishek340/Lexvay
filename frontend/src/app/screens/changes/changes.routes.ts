import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { CHANGES_ICONS, Changes } from './changes';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Changes, providers: [provideIcons(CHANGES_ICONS)], title: 'Changes · Lexvay' }] satisfies Routes;
