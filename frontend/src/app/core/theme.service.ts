import { Injectable, signal } from '@angular/core';

export type Theme = 'system' | 'light' | 'dark';
const KEY = 'lexvay.theme';

/** Light and dark themes via a data-theme attribute; the preference is a per-browser convenience. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.read());

  constructor() {
    this.apply(this.theme());
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    try { localStorage.setItem(KEY, theme); } catch { /* storage may be unavailable */ }
    this.apply(theme);
  }

  private read(): Theme {
    try {
      const v = localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' ? v : 'system';
    } catch {
      return 'system';
    }
  }

  private apply(theme: Theme): void {
    const root = document.documentElement;
    if (theme === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', theme);
    }
  }
}
