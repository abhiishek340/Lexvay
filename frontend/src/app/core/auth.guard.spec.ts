import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, GuardResult, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiService } from './api.service';
import { landingGuard } from './auth.guard';
import { Program } from './models';

const program = (id: string): Program => ({ id, name: id, licenseType: 'L', catalogYear: '2026-27', jurisdictions: ['CA'], createdAt: '' });

describe('landingGuard', () => {
  let answer: Observable<Program[]>;

  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: { programs: () => answer } }] }));

  async function landing(programs: Observable<Program[]>): Promise<GuardResult> {
    answer = programs;
    const result = await TestBed.runInInjectionContext(() => landingGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    return result as GuardResult;
  }

  it('redirects to the overview of the only program, inside the same navigation', async () => {
    const result = await landing(of([program('p1')]));
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/programs/p1/overview');
  });

  it('lets the screen list the programs when there are several or none', async () => {
    expect(await landing(of([program('p1'), program('p2')]))).toBe(true);
    expect(await landing(of([]))).toBe(true);
  });

  it('lets the screen show the failure when the programs cannot be loaded', async () => {
    expect(await landing(throwError(() => new Error('offline')))).toBe(true);
  });
});
