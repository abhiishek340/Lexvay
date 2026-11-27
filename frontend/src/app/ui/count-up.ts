import { DestroyRef, Directive, ElementRef, effect, inject, input, untracked } from '@angular/core';
import { MotionPreferences } from '../core/motion';

const DURATION = 600;
const EASE_OUT = 'cubic-bezier(.16, 1, .3, 1)';

/**
 * A number that counts up from 0 the first time it is shown, over 600 ms ease-out (DESIGN.md "Numbers"):
 *   <span [lxCountUp]="toReview()"></span>   <span [lxCountUp]="72" suffix="%"></span>
 * The element's text is the number; later values replace it at once. The timing comes from the Web Animations API
 * (an effect-less animation whose eased progress drives the text), reduced motion shows the final value, and the
 * element reserves the final value's width so nothing around it moves while it counts.
 */
@Directive({
  selector: '[lxCountUp]',
  host: { '[style.display]': '"inline-block"', '[style.min-width.ch]': 'width()' },
})
export class CountUp {
  readonly lxCountUp = input.required<number>();
  readonly suffix = input('');

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly motion = inject(MotionPreferences);
  private animation: Animation | null = null;
  private frame = 0;
  private counted = false;

  constructor() {
    effect(() => {
      const value = this.lxCountUp();
      const suffix = this.suffix();
      untracked(() => this.show(value, suffix));
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  /** Tabular figures make every digit one `ch` wide, so the final text's length is its width. */
  width(): number {
    return `${Math.round(this.lxCountUp())}${this.suffix()}`.length;
  }

  private show(value: number, suffix: string): void {
    this.stop();
    const final = Math.round(value);
    if (this.counted || this.motion.reduced() || final === 0 || typeof this.el.animate !== 'function') {
      this.counted = true;
      this.el.textContent = `${final}${suffix}`;
      return;
    }
    this.counted = true;
    const timing = new Animation(new KeyframeEffect(null, [], { duration: DURATION, easing: EASE_OUT, fill: 'forwards' }));
    this.animation = timing;
    timing.play();
    const tick = (): void => {
      const progress = Number(timing.effect?.getComputedTiming().progress ?? 1);
      this.el.textContent = `${Math.round(final * progress)}${suffix}`;
      this.frame = timing.playState === 'finished' ? 0 : requestAnimationFrame(tick);
    };
    timing.finished.then(() => { this.el.textContent = `${final}${suffix}`; }, () => undefined);
    tick();
  }

  private stop(): void {
    cancelAnimationFrame(this.frame);
    this.animation?.cancel();
    this.animation = null;
  }
}
