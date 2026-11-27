import { Routes } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { adminGuard } from '../../core/auth.guard';
import { PEOPLE_ICONS, People } from './people';

/** Administrators only (the server's rule as well); the screen and its icons are one lazy chunk. */
export default [{ path: '', component: People, canActivate: [adminGuard], providers: [provideIcons(PEOPLE_ICONS)], title: 'People · Lexvay' }] satisfies Routes;
