import { Routes } from '@angular/router';
import { Coverage } from './coverage';

/** The screen in one lazy chunk. It shows no icons beyond the shell's: its marks are drawn shapes (ui/mark-shape.ts). */
export default [{ path: '', component: Coverage, title: 'Coverage · Lexvay' }] satisfies Routes;
