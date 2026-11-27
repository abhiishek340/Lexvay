import { DestroyRef, Directive, ElementRef, afterNextRender, inject, signal } from '@angular/core';

/**
 * Shows a long text in three lines until it is opened. overflowing() says whether the text is cut, so a screen
 * offers its expand control only where there is more to read.
 */
@Directive({
  selector: '[lxClamp]',
  exportAs: 'lxClamp',
  host: { '[class.lx-clamped]': '!open()' },
})
export class Clamp {
  readonly open = signal(false);
  readonly overflowing = signal(false);

  constructor() {
    const el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const measure = (): void => {
      if (!this.open()) { this.overflowing.set(el.scrollHeight > el.clientHeight + 1); }
    };
    let observer: ResizeObserver | null = null;
    afterNextRender(() => {
      measure();
      if (typeof ResizeObserver === 'function') {
        observer = new ResizeObserver(measure);
        observer.observe(el);
      }
    });
    inject(DestroyRef).onDestroy(() => observer?.disconnect());
  }

  toggle(): void {
    this.open.update((open) => !open);
  }
}
