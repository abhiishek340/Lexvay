import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { DestroyRef } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Countdown, busyRetryAfter } from './busy';

const refused = (status: number, retryAfter?: string) =>
  new HttpErrorResponse({ status, headers: retryAfter === undefined ? new HttpHeaders() : new HttpHeaders({ 'Retry-After': retryAfter }) });

describe('busyRetryAfter', () => {
  it("reads the seconds of the hashing gate's 503", () => {
    expect(busyRetryAfter(refused(503, '2'))).toBe(2);
    expect(busyRetryAfter(refused(503, ' 5 '))).toBe(5);
  });

  it('keeps the wait between one second and a minute', () => {
    expect(busyRetryAfter(refused(503, '0'))).toBe(1);
    expect(busyRetryAfter(refused(503, '3600'))).toBe(60);
  });

  it('is not the busy case for other answers', () => {
    expect(busyRetryAfter(refused(503))).toBeNull();
    expect(busyRetryAfter(refused(503, 'Fri, 27 Nov 2026 16:00:00 GMT'))).toBeNull();
    expect(busyRetryAfter(refused(429, '30'))).toBeNull();
    expect(busyRetryAfter(refused(401))).toBeNull();
    expect(busyRetryAfter(new Error('offline'))).toBeNull();
  });
});

describe('Countdown', () => {
  let destroy: () => void;
  const destroyRef = { onDestroy: (fn: () => void) => { destroy = fn; return () => undefined; } } as unknown as DestroyRef;

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('counts down once a second and stops at zero', () => {
    const countdown = new Countdown(destroyRef);
    expect(countdown.remaining()).toBe(0);
    countdown.start(2);
    expect(countdown.remaining()).toBe(2);
    vi.advanceTimersByTime(1000);
    expect(countdown.remaining()).toBe(1);
    vi.advanceTimersByTime(1000);
    expect(countdown.remaining()).toBe(0);
    vi.advanceTimersByTime(5000);
    expect(countdown.remaining()).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('starts over when refused again, and stops with its component', () => {
    const countdown = new Countdown(destroyRef);
    countdown.start(3);
    vi.advanceTimersByTime(1000);
    countdown.start(2);
    expect(countdown.remaining()).toBe(2);
    expect(vi.getTimerCount()).toBe(1);
    destroy();
    expect(countdown.remaining()).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });
});
