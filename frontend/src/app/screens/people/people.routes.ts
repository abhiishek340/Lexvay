import { Routes } from '@angular/router';
import { adminGuard } from '../../core/auth.guard';
import { People } from './people';

/** Administrators only (the server's rule as well); the screen is one lazy chunk and shows no icons beyond the shell's. */
export default [{ path: '', component: People, canActivate: [adminGuard], title: 'People · Lexvay' }] satisfies Routes;
