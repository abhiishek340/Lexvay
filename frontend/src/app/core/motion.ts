import { DestroyRef, Injectable, inject, signal } from '@angular/core';

const QUERY = '(prefers-reduced-motion: reduce)';

/**
 * Whether the person asked for reduced motion, as a signal that follows the system setting while the page is open.
 * Every decision made in TypeScript about motion (route transitions) reads it here; CSS animations follow the same
 * media query in styles.css.
 */
@Injectable({ providedIn: 'root' })
export class MotionPreferences {
  private readonly media = typeof matchMedia === 'function' ? matchMedia(QUERY) : null;
  readonly reduced = signal(this.media?.matches ?? false);

  constructor() {
    const onChange = (e: MediaQueryListEvent): void => this.reduced.set(e.matches);
    this.media?.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => this.media?.removeEventListener('change', onChange));
  }
}
