import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { AUDIT_ICONS, Audit } from './audit';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Audit, providers: [provideIcons(AUDIT_ICONS)], title: 'Audit trail · Lexvay' }] satisfies Routes;
