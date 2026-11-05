const AUTH_PAGES = ['/login', '/register', '/forgot-password', '/reset-password']

/** Returns a same-site path to continue to after sign-in, or "/" for anything unsafe (open-redirect guard). */
export function safeNext(next: string | null | undefined): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/'
  if (AUTH_PAGES.some((p) => next === p || next.startsWith(`${p}?`) || next.startsWith(`${p}/`))) return '/'
  return next
}
