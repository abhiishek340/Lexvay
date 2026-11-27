import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { describe, expect, it } from 'vitest';
import { App } from './app';
import { LEXVAY_ICONS } from './theme/icons';

describe('App', () => {
  it('renders the skip link and the main landmark, and no navigation before anyone signs in', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), providePrimeNG({}), provideIcons(LEXVAY_ICONS), MessageService],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('a.lx-skip')?.getAttribute('href')).toBe('#main');
    expect(root.querySelector('main#main')).not.toBeNull();
    expect(root.querySelector('nav')).toBeNull();
    expect(document.documentElement.getAttribute('data-theme')).toMatch(/^(light|dark)$/);
  });
});
