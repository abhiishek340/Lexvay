import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_CHANGED, accessInterceptor } from './access';
import { ProgramContext } from './program-context';
import { SessionService } from './session.service';

@Component({ template: '' })
class Blank {}

const SIGNED_IN = { authenticated: true, user: { id: 'u1', tenantId: 't1', email: 'a@x.example', displayName: 'Ann Lee', role: 'CONTRIBUTOR' }, csrfHeaderName: 'X-XSRF-TOKEN', tenantName: 'T', demo: false };
const SIGNED_OUT = { authenticated: false, user: null, csrfHeaderName: 'X-XSRF-TOKEN', tenantName: null, demo: false };
const problem = (detail: string) => ({ type: 'about:blank', title: 'Unauthorized', status: 401, detail });

describe('access changed (401 "Your access changed. Sign in again.")', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let router: Router;
  let session: SessionService;
  let context: ProgramContext;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([accessInterceptor])), provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: Blank }, { path: 'setup', component: Blank }, { path: '**', component: Blank }]),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    session = TestBed.inject(SessionService);
    context = TestBed.inject(ProgramContext);
    await router.navigateByUrl('/programs/p1/overview');
    const loaded = session.load();
    backend.expectOne('/api/v1/auth/session').flush(SIGNED_IN);
    await loaded;
  });

  it('signs out here, clears the program, opens sign-in with the sentence and still fails the call', async () => {
    const clear = vi.spyOn(context, 'clear');
    const call = firstValueFrom(http.get('/api/v1/audit'));
    backend.expectOne('/api/v1/audit').flush(problem(ACCESS_CHANGED), { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(session.authenticated()).toBe(false);
    expect(session.loaded()).toBe(false);
    expect(session.notice()).toBe(ACCESS_CHANGED);
    expect(clear).toHaveBeenCalled();
    await vi.waitFor(() => expect(router.url).toBe('/login'));
  });

  it('leaves any other 401 alone: a wrong password is not a changed access', async () => {
    const call = firstValueFrom(http.post('/api/v1/auth/login', 'username=a&password=b'));
    backend.expectOne('/api/v1/auth/login').flush(problem('Email or password is incorrect'), { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(session.authenticated()).toBe(true);
    expect(session.notice()).toBeNull();
    expect(router.url).toBe('/programs/p1/overview');
  });

  it('keeps someone choosing a password on that page', async () => {
    await router.navigateByUrl('/setup');
    const call = firstValueFrom(http.get('/api/v1/auth/session'));
    backend.expectOne('/api/v1/auth/session').flush(problem(ACCESS_CHANGED), { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(session.notice()).toBe(ACCESS_CHANGED);
    expect(router.url).toBe('/setup');
  });

  it('a session call answered 401 reads as signed out: the next call finds the session gone', async () => {
    const loaded = session.load();
    backend.expectOne('/api/v1/auth/session').flush(problem(ACCESS_CHANGED), { status: 401, statusText: 'Unauthorized' });
    await vi.waitFor(() => backend.expectOne('/api/v1/auth/session').flush(SIGNED_OUT));
    await expect(loaded).resolves.toMatchObject({ authenticated: false });
    expect(session.loaded()).toBe(true);
    expect(session.authenticated()).toBe(false);
    expect(session.notice()).toBe(ACCESS_CHANGED);
  });

  it('a successful sign-in clears the notice', async () => {
    session.end(ACCESS_CHANGED);
    const login = session.login('a@x.example', 'correct horse battery');
    await vi.waitFor(() => backend.expectOne('/api/v1/auth/session').flush(SIGNED_OUT));
    await vi.waitFor(() => backend.expectOne('/api/v1/auth/login').flush(SIGNED_IN.user));
    await vi.waitFor(() => backend.expectOne('/api/v1/auth/session').flush(SIGNED_IN));
    await login;
    expect(session.notice()).toBeNull();
    expect(session.authenticated()).toBe(true);
  });
});
