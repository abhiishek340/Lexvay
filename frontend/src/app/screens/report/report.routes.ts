import { Routes } from '@angular/router';
import { Report } from './report';

/** The screen in its own lazy chunk; it shows no icons beyond the shell's. */
export default [{ path: '', component: Report, title: 'Evidence report · Lexvay' }] satisfies Routes;
