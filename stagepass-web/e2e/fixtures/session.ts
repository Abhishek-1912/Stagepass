import type { Page } from '@playwright/test'

/** Base64url without padding, as in a JWT. */
function b64url(value: object): string {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

/**
 * Puts a fake Supabase session in localStorage before any page script runs, so the app treats the browser as
 * signed in without calling Supabase (the access token is valid for an hour, so supabase-js does not refresh it).
 */
export async function signIn(page: Page, email = 'asha@stagepass.test') {
  const now = Math.floor(Date.now() / 1000)
  const user = { id: 'u-e2e', aud: 'authenticated', role: 'authenticated', email, app_metadata: {}, user_metadata: { full_name: 'Asha Rao' } }
  const accessToken = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: user.id, email, exp: now + 3600, aud: 'authenticated' })}.sig`
  const session = {
    access_token: accessToken,
    refresh_token: 'e2e-refresh',
    token_type: 'bearer',
    expires_in: 3600,
    expires_at: now + 3600,
    user,
  }
  await page.addInitScript((value) => {
    window.localStorage.setItem('sb-test-auth-token', value)
  }, JSON.stringify(session))
}
