import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, GuardResult, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProgramContext } from '../../core/program-context';
import { RuleVersionGroup } from '../../core/rules';
import { openingVersionGuard } from './opening-version.guard';

const group = (id: string, status: 'APPROVED' | 'NEEDS_APPROVAL'): RuleVersionGroup =>
  ({ code: 'CO', name: 'Colorado', rows: [{ view: { id }, status }] }) as unknown as RuleVersionGroup;

describe('openingVersionGuard', () => {
  let groups: RuleVersionGroup[];
  let ensured: string[];

  beforeEach(() => {
    groups = [];
    ensured = [];
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProgramContext, useValue: { ensure: async (id: string) => { ensured.push(id); }, ruleVersionGroups: () => groups } }],
    });
  });

  async function enter(params: Record<string, string>, queryParams: Record<string, string> = {}): Promise<GuardResult> {
    const route = { params, queryParams } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => openingVersionGuard(route, {} as RouterStateSnapshot)) as Promise<GuardResult>;
  }

  it('lets an address that names a version through, without loading anything', async () => {
    expect(await enter({ programId: 'p1', ruleVersionId: 'rv1' })).toBe(true);
    expect(ensured).toEqual([]);
  });

  it('replaces the bare address by the version that opens, keeping the query', async () => {
    groups = [group('rv7', 'NEEDS_APPROVAL')];
    const result = await enter({ programId: 'p1' }, { new: '1' });
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/programs/p1/rules/rv7?new=1');
    expect(ensured).toEqual(['p1']);
  });

  it('stays on the bare address when there is no version to open', async () => {
    expect(await enter({ programId: 'p1' })).toBe(true);
  });
});
