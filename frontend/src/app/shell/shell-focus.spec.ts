import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { focusHeadingWhenRendered } from './shell';

describe('focusHeadingWhenRendered', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'performance'] });
    document.body.innerHTML = '<button id="link">Coverage</button><main></main><button id="menu">More</button>';
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  function render(): HTMLElement {
    const heading = document.createElement('h1');
    heading.textContent = 'Coverage';
    document.querySelector('main')!.append(heading);
    return heading;
  }

  it('focuses the heading once the screen renders it', () => {
    document.getElementById('link')!.focus();
    focusHeadingWhenRendered();
    vi.advanceTimersToNextFrame();
    const heading = render();
    vi.advanceTimersToNextFrame();
    expect(document.activeElement).toBe(heading);
    expect(heading.getAttribute('tabindex')).toBe('-1');
  });

  it('still focuses the heading when the control that navigated has left the page', () => {
    document.getElementById('link')!.focus();
    focusHeadingWhenRendered();
    document.getElementById('link')!.remove();
    const heading = render();
    vi.advanceTimersToNextFrame();
    expect(document.activeElement).toBe(heading);
  });

  it('leaves focus where the person moved it while the screen loaded', () => {
    document.getElementById('link')!.focus();
    focusHeadingWhenRendered();
    vi.advanceTimersToNextFrame();
    const menu = document.getElementById('menu')!;
    menu.focus();
    render();
    vi.advanceTimersToNextFrame();
    expect(document.activeElement).toBe(menu);
  });
});
