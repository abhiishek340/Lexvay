/**
 * PrimeNG 22 is distributed under the PrimeUI License and verifies a license key offline at start-up;
 * without a key it shows an "Invalid PrimeUI License" notice (docs/decisions/WS-UI.md, D-080). The key is
 * not kept in the repository: pass it at build time, for example
 *   ng build --define "LEXVAY_PRIMEUI_LICENSE='<key>'"
 * Without the define the identifier is undeclared at runtime and the key is empty.
 */
declare const LEXVAY_PRIMEUI_LICENSE: string | undefined;

export const PRIMEUI_LICENSE: string = typeof LEXVAY_PRIMEUI_LICENSE === 'string' ? LEXVAY_PRIMEUI_LICENSE : '';
