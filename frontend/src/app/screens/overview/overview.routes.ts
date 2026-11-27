import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { OVERVIEW_ICONS, Overview } from './overview';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Overview, providers: [provideIcons(OVERVIEW_ICONS)], title: 'Program overview · Lexvay' }] satisfies Routes;
