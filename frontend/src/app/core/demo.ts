import { Role } from './models';

/** One fictional demo account the sign-in page offers as a chip that fills the email. */
export interface DemoAccount { readonly email: string; readonly name: string; readonly role: Role }

/**
 * The demo tenant's accounts come from the build, not from the server: before sign-in `/auth/session` cannot say
 * which tenant is the demo (its `demo` flag describes the signed-in user's tenant), so a build that serves the
 * fictional seed declares the seed's accounts (`angular.json`, development configuration; for another build,
 * `ng build --define 'LEXVAY_DEMO_ACCOUNTS=[...]'`). Production builds declare none, and no chips appear (D-314).
 */
declare const LEXVAY_DEMO_ACCOUNTS: readonly DemoAccount[] | undefined;

export const DEMO_ACCOUNTS: readonly DemoAccount[] = typeof LEXVAY_DEMO_ACCOUNTS === 'undefined' ? [] : LEXVAY_DEMO_ACCOUNTS;
