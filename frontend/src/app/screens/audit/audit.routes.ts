import { Routes } from '@angular/router';
import { Audit } from './audit';

/** The screen in its own lazy chunk; it shows no icons beyond the shell's. */
export default [{ path: '', component: Audit, title: 'Audit trail · Lexvay' }] satisfies Routes;
