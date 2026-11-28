import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Help } from './help';

function render(keys = [{ keys: 'J', description: 'Next requirement' }]) {
  const fixture = TestBed.createComponent(Help);
  fixture.componentRef.setInput('terms', [{ term: 'Signing', definition: 'A signer signs.' }]);
  fixture.componentRef.setInput('keys', keys);
  fixture.detectChanges();
  return fixture;
}

describe('Help', () => {
  it('is one ghost "?" button named Help, closed until asked', () => {
    const fixture = render();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    expect(button.textContent?.trim()).toBe('?');
    expect(button.getAttribute('aria-label')).toBe('Help');
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('opens from a key press under its button, with the terms and then the keys', async () => {
    const fixture = render();
    fixture.componentInstance.show();
    fixture.detectChanges();
    await fixture.whenStable();
    const panel = document.querySelector('.lx-help-body')!;
    expect(panel.querySelector('.lx-help-list dt')?.textContent).toBe('Signing');
    expect(panel.querySelector('.lx-help-keys kbd')?.textContent).toBe('J');
    expect(panel.querySelector('.lx-help-keys dd')?.textContent).toBe('Next requirement');
  });

  it('shows no keys section on a screen without shortcuts', async () => {
    const fixture = render([]);
    fixture.componentInstance.show();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.querySelector('.lx-help-body')).not.toBeNull();
    expect(document.querySelector('.lx-help-keys-title')).toBeNull();
  });
});
