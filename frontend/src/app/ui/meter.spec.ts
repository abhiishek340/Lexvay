import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Meter, meterRuns } from './meter';

describe('meterRuns', () => {
  it('draws each segment to scale in the fixed order, the rest of the total left empty', () => {
    expect(meterRuns([{ tone: 'missing', value: 1 }, { tone: 'found', value: 4 }, { tone: 'ambiguous', value: 1 }], 8)).toEqual([
      { tone: 'found', percent: 50 }, { tone: 'ambiguous', percent: 12.5 }, { tone: 'missing', percent: 12.5 },
    ]);
  });

  it('drops empty segments, never overruns the bar, and draws nothing against a zero total', () => {
    expect(meterRuns([{ tone: 'found', value: 0 }, { tone: 'decided', value: 3 }], 4)).toEqual([{ tone: 'decided', percent: 75 }]);
    expect(meterRuns([{ tone: 'found', value: 7 }, { tone: 'missing', value: 5 }], 10)).toEqual([{ tone: 'found', percent: 70 }, { tone: 'missing', percent: 30 }]);
    expect(meterRuns([{ tone: 'found', value: 2 }], 0)).toEqual([]);
  });
});

describe('Meter', () => {
  it('shows its words and keeps the bar from assistive technology', () => {
    const fixture = TestBed.createComponent(Meter);
    fixture.componentRef.setInput('segments', [{ tone: 'decided', value: 6 }]);
    fixture.componentRef.setInput('total', 8);
    fixture.componentRef.setInput('text', '6 of 8 decided');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.lx-meter-text')?.textContent).toBe('6 of 8 decided');
    expect(el.querySelector('.lx-bar')?.getAttribute('aria-hidden')).toBe('true');
    const runs = [...el.querySelectorAll<HTMLElement>('.lx-run')];
    expect(runs.map((r) => [r.dataset['tone'], r.style.width])).toEqual([['decided', '75%']]);
  });
});
