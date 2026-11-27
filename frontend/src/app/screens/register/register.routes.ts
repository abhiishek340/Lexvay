import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { REGISTER_ICONS, Register } from './register';

/** The screen with its own icons, in one lazy chunk: route providers are built when the app starts, so they live here. */
export default [{ path: '', component: Register, providers: [provideIcons(REGISTER_ICONS)], title: 'Determination register · Lexvay' }] satisfies Routes;
