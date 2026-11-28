import { Routes } from '@angular/router';
import { Changes } from './changes';

/** The screen in its own lazy chunk; it shows no icons beyond the shell's. */
export default [{ path: '', component: Changes, title: 'Changes · Lexvay' }] satisfies Routes;
