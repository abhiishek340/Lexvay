import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SessionView } from './models';
import { SessionService } from './session.service';

const USER = { id: 'u1', tenantId: 't1', email: 'a@x.example', displayName: 'Ann Lee', role: 'CONTRIBUTOR' } as const;
const session = (overrides: Partial<SessionView>): SessionView =>
  ({ authenticated: true, user: USER, csrfHeaderName: 'X-XSRF-TOKEN', tenantName: 'Tenant', demo: false, ...overrides });

/** The fictional-data banner of the shell follows `demo`, which the server derives from the tenant's data mode (MODE-1). */
describe('SessionService demo flag', () => {
  let service: SessionService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SessionService);
    backend = TestBed.inject(HttpTestingController);
  });

  async function load(view: SessionView): Promise<void> {
    const loading = service.load();
    backend.expectOne('/api/v1/auth/session').flush(view);
    await loading;
  }

  it('is off until a session has loaded', () => {
    expect(service.demo()).toBe(false);
  });

  it('shows the banner for a demo tenant and names the tenant', async () => {
    await load(session({ demo: true, tenantName: 'Northfield University' }));
    expect(service.demo()).toBe(true);
    expect(service.tenantName()).toBe('Northfield University');
  });

  it('shows no banner for a tenant with real data', async () => {
    await load(session({ demo: false }));
    expect(service.demo()).toBe(false);
  });

  it('takes the banner away when the session ends', async () => {
    await load(session({ demo: true }));
    service.end('Your access changed. Sign in again.');
    expect(service.demo()).toBe(false);
  });
});
