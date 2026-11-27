import { DestroyRef, Injectable, inject, signal } from '@angular/core';

export type Theme = 'system' | 'light' | 'dark';
const KEY = 'lexvay.theme';

/**
 * The preference is a per-browser convenience. The document always carries a concrete data-theme
 * ("light" or "dark"), which is the single selector the PrimeNG preset and the application CSS read;
 * "system" resolves it from prefers-color-scheme and follows changes while the page is open.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(read());
  readonly resolved = signal<'light' | 'dark'>('light');
  private readonly media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;

  constructor() {
    const onChange = (): void => this.apply();
    this.media?.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => this.media?.removeEventListener('change', onChange));
    this.apply();
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    try { localStorage.setItem(KEY, theme); } catch { /* storage may be unavailable; the choice then lasts for this page */ }
    this.apply();
  }

  private apply(): void {
    const theme = this.theme();
    const resolved = theme === 'system' ? (this.media?.matches ? 'dark' : 'light') : theme;
    this.resolved.set(resolved);
    document.documentElement.setAttribute('data-theme', resolved);
  }
}

function read(): Theme {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}
