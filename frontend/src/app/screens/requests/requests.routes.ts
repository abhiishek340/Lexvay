import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { REQUESTS_ICONS, Requests } from './requests';

/** The screen with its own icons, in one lazy chunk: route providers are built at start-up, so they live here. */
export default [{ path: '', component: Requests, providers: [provideIcons(REQUESTS_ICONS)], title: 'Evidence requests · Lexvay' }] satisfies Routes;
