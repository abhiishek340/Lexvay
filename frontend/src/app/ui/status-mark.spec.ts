import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { MarkTone, STATUS_MARK, Status, StatusMark } from './status-mark';

/** What DESIGN.md "Status marks" fixes for each status: the shape drawn, its colour family, and the word beside it. */
const TABLE: [Status, string, string, string][] = [
  ['EVIDENCE_FOUND', 'disc', 'found', 'Evidence found'],
  ['ACCEPTED', 'disc', 'found', 'Accepted'],
  ['EVIDENCE_MISSING', 'square', 'missing', 'Evidence missing'],
  ['GAP_CONFIRMED', 'square', 'missing', 'Gap confirmed'],
  ['AMBIGUOUS', 'half', 'neutral', 'Ambiguous'],
  ['NOT_REVIEWED', 'ring', 'quiet', 'Not reviewed'],
  ['EVIDENCE_REQUESTED', 'ring-dot', 'neutral', 'Evidence requested'],
  ['OUTDATED', 'dashed-ring', 'neutral', 'Outdated'],
  ['BLOCKED', 'open-square', 'neutral', 'Blocked'],
];

function render(status: Status, inputs: { label?: string; quiet?: boolean; tone?: MarkTone } = {}): HTMLElement {
  const fixture = TestBed.createComponent(StatusMark);
  fixture.componentRef.setInput('status', status);
  if (inputs.label) { fixture.componentRef.setInput('label', inputs.label); }
  if (inputs.quiet) { fixture.componentRef.setInput('quiet', true); }
  if (inputs.tone) { fixture.componentRef.setInput('tone', inputs.tone); }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

/** The SVG a shape draws, as element names and the attributes that tell the shapes apart. */
function drawing(el: HTMLElement): string {
  return [...el.querySelectorAll('svg > *')].map((n) =>
    `${n.tagName}.${n.getAttribute('class')}${n.hasAttribute('stroke-dasharray') ? '.dashed' : ''}`).join(' ');
}

describe('StatusMark', () => {
  it('maps every status to its shape, colour family and word', () => {
    expect(Object.keys(STATUS_MARK).sort()).toEqual(TABLE.map(([s]) => s).sort());
    for (const [status, shape, tone, word] of TABLE) {
      expect(STATUS_MARK[status]).toEqual({ shape, tone, word });
      const el = render(status);
      expect(el.getAttribute('data-tone')).toBe(tone);
      expect(el.querySelector('.lx-word')?.textContent?.trim()).toBe(word);
    }
  });

  it('draws seven distinct shapes, found and missing differing in shape and not only in colour', () => {
    const drawings = new Map(TABLE.map(([status, shape]) => [shape, drawing(render(status))]));
    expect(new Set(drawings.values()).size).toBe(7);
    expect(drawings.get('disc')).toBe('circle.lx-fill');
    expect(drawings.get('square')).toBe('rect.lx-fill');
    expect(drawings.get('ring')).toBe('circle.lx-stroke');
    expect(drawings.get('dashed-ring')).toBe('circle.lx-stroke.dashed');
  });

  it('takes another tone where a borrowed shape must not carry its colour, keeping the shape', () => {
    const el = render('ACCEPTED', { label: 'Approved', tone: 'ink' });
    expect(el.getAttribute('data-tone')).toBe('ink');
    expect(drawing(el)).toBe('circle.lx-fill');
  });

  it('hides the mark from assistive technology and always keeps the word', () => {
    const el = render('AMBIGUOUS', { label: 'Ambiguous, not reviewed', quiet: true });
    expect(el.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    const word = el.querySelector('.lx-word')!;
    expect(word.textContent?.trim()).toBe('Ambiguous, not reviewed');
    expect(word.classList.contains('lx-sr-only')).toBe(true);
  });
});
