import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { MarkShape, MarkShapeIcon } from './mark-shape';

function render(shape: MarkShape, outdated = false): HTMLElement {
  const fixture = TestBed.createComponent(MarkShapeIcon);
  fixture.componentRef.setInput('shape', shape);
  fixture.componentRef.setInput('outdated', outdated);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('MarkShapeIcon', () => {
  it('draws the shape alone at 10 px, hidden from assistive technology', () => {
    const el = render('disc');
    const svg = el.querySelector('svg')!;
    expect(svg.getAttribute('width')).toBe('10');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(el.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelectorAll('svg > circle')).toHaveLength(1);
    expect(el.hasAttribute('data-outdated')).toBe(false);
  });

  it('draws a partly outdated mark inside the dashed ring of the outdated mark, scaled to 18 px with the same eight dashes', () => {
    const el = render('ring-dot', true);
    const svg = el.querySelector('svg')!;
    expect(svg.getAttribute('width')).toBe('18');
    expect(svg.getAttribute('viewBox')).toBe('-4 -4 18 18');
    const [ring, ...shape] = [...svg.children];
    const dashes = (circle: Element) => {
      const [dash, gap] = circle.getAttribute('stroke-dasharray')!.split(' ').map(Number);
      return Math.round((2 * Math.PI * Number(circle.getAttribute('r'))) / (dash + gap));
    };
    expect(ring.getAttribute('r')).toBe('8.25');
    expect(dashes(ring)).toBe(dashes(render('dashed-ring').querySelector('circle')!));
    expect(shape.map((n) => n.getAttribute('class'))).toEqual(['lx-stroke', 'lx-fill']);
    expect(el.getAttribute('data-outdated')).toBe('true');
  });
});
