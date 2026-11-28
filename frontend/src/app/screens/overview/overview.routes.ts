import { Routes } from '@angular/router';
import { Overview } from './overview';

/** The screen in its own lazy chunk; it shows no icons beyond the shell's. */
export default [{ path: '', component: Overview, title: 'Program overview · Lexvay' }] satisfies Routes;
