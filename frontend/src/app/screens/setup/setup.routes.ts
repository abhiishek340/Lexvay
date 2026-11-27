import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { SETUP_ICONS, Setup } from './setup';

/** Anonymous: the person has a setup code, not a session. The screen and its icons are one lazy chunk. */
export default [{ path: '', component: Setup, providers: [provideIcons(SETUP_ICONS)], title: 'Choose a password · Lexvay' }] satisfies Routes;
