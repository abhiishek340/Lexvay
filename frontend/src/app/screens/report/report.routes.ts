import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { REPORT_ICONS, Report } from './report';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Report, providers: [provideIcons(REPORT_ICONS)], title: 'Evidence report · Lexvay' }] satisfies Routes;
