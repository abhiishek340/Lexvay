import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, signal } from '@angular/core';

/** What sign-in and choosing a password say while every password hash is in use (SEC-2, D-407). */
export const BUSY = 'Sign-in is busy. Try again in a moment.';

/** The longest wait the screens honour; a larger header is the server's mistake, not a reason to lock the form. */
const MAX_WAIT_SECONDS = 60;

/**
 * The seconds to wait when the server refused because it is busy: a 503 carrying `Retry-After` in seconds (the hashing
 * gate sends 2). Anything else, including a 503 without the header (a proxy with no backend), is not this case: null.
 */
export function busyRetryAfter(e: unknown): number | null {
  if (!(e instanceof HttpErrorResponse) || e.status !== 503) { return null; }
  const header = e.headers.get('Retry-After')?.trim() ?? '';
  if (!/^\d+$/.test(header)) { return null; }
  return Math.min(Math.max(Number(header), 1), MAX_WAIT_SECONDS);
}

/**
 * Seconds left before a refused action may be tried again, counted down once a second; 0 when nothing is waiting.
 * Created in a component's field initialiser, it stops with the component.
 */
export class Countdown {
  private readonly left = signal(0);
  readonly remaining = this.left.asReadonly();
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor(destroyRef: DestroyRef) {
    destroyRef.onDestroy(() => this.stop());
  }

  start(seconds: number): void {
    this.stop();
    this.left.set(seconds);
    this.timer = setInterval(() => {
      this.left.update((n) => n - 1);
      if (this.left() <= 0) { this.stop(); }
    }, 1000);
  }

  private stop(): void {
    clearInterval(this.timer);
    this.timer = undefined;
    this.left.set(0);
  }
}
