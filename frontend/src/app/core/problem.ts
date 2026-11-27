/** The server's problem detail (RFC 9457) when there is one, otherwise the given fallback sentence. */
export function problemDetail(e: unknown, fallback: string): string {
  const err = e as { error?: { detail?: unknown }; message?: unknown } | null;
  if (typeof err?.error?.detail === 'string' && err.error.detail) {
    return err.error.detail;
  }
  if (e instanceof Error && e.message) {
    return e.message;
  }
  return fallback;
}
