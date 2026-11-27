import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { COVERAGE_ICONS, Coverage } from './coverage';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Coverage, providers: [provideIcons(COVERAGE_ICONS)], title: 'Coverage · Lexvay' }] satisfies Routes;
