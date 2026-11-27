import { UrlSegment } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { setupMatcher } from './app.routes';

const segments = (path: string): UrlSegment[] => path.split('/').map((p) => new UrlSegment(p, {}));

describe('setupMatcher', () => {
  it('serves a new program and an existing program from one route', () => {
    expect(setupMatcher(segments('programs/new'))).toEqual({ consumed: segments('programs/new') });
    const match = setupMatcher(segments('programs/p1/setup'));
    expect(match?.posParams?.['programId'].path).toBe('p1');
    expect(match?.consumed.map((s) => s.path)).toEqual(['programs', 'p1', 'setup']);
  });

  it('leaves every other program screen to the program routes', () => {
    expect(setupMatcher(segments('programs/p1'))).toBeNull();
    expect(setupMatcher(segments('programs/p1/overview'))).toBeNull();
    expect(setupMatcher(segments('programs/p1/setup/more'))).toBeNull();
    expect(setupMatcher(segments('register'))).toBeNull();
  });
});
