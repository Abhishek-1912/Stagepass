import { beforeEach, describe, expect, test, vi } from 'vitest'

const auth = vi.hoisted(() => ({
  getSession: vi.fn(),
  refreshSession: vi.fn(),
  signOut: vi.fn(),
}))
const nav = vi.hoisted(() => ({ redirectToLogin: vi.fn() }))

vi.mock('@/lib/supabase', () => ({ supabase: { auth } }))
vi.mock('@/lib/navigation', () => nav)

import { api } from './client'
import { ApiError } from './ApiError'

const json = (status: number, body: unknown, contentType = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })

function session(token: string) {
  return { data: { session: { access_token: token } }, error: null }
}

describe('api client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    auth.getSession.mockReset().mockResolvedValue(session('abc'))
    auth.refreshSession.mockReset()
    auth.signOut.mockReset().mockResolvedValue({ error: null })
    nav.redirectToLogin.mockReset()
  })

  test('429 becomes a friendly ApiError with the wait', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 429, code: 'RATE_LIMITED', detail: 'Too many requests. Please wait a moment and try again.' }), {
        status: 429,
        headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '12' },
      }),
    )
    await expect(api('/bookings', { method: 'POST' })).rejects.toMatchObject({
      status: 429,
      code: 'RATE_LIMITED',
      detail: 'Too many requests. Try again in 12 seconds.',
    })
  })

    test('parses problem details into ApiError', async () => {
    fetchMock.mockResolvedValue(
      json(409, { status: 409, code: 'ACCOUNT_CONFLICT', detail: 'Email in use' }, 'application/problem+json'),
    )

    const error = await api('/me').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'ACCOUNT_CONFLICT', detail: 'Email in use' })
  })

  test('exposes field errors from validation problems', async () => {
    fetchMock.mockResolvedValue(
      json(400, { code: 'VALIDATION_FAILED', detail: 'Request validation failed', errors: [{ field: 'phone', message: 'bad' }] }),
    )

    const error = (await api('/me', { method: 'PATCH', json: {} }).catch((e: unknown) => e)) as ApiError

    expect(error.fieldErrors).toEqual([{ field: 'phone', message: 'bad' }])
  })

  test('attaches bearer token and JSON body', async () => {
    fetchMock.mockResolvedValue(json(200, { ok: true }))

    await api('/me', { method: 'PATCH', json: { city: 'Pune' } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://api.test/api/v1/me')
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer abc')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(init.body).toBe('{"city":"Pune"}')
  })

  test('sends no Authorization header when signed out', async () => {
    auth.getSession.mockResolvedValue({ data: { session: null }, error: null })
    fetchMock.mockResolvedValue(json(200, []))

    await api('/public/events')

    expect(new Headers(fetchMock.mock.calls[0][1].headers).has('Authorization')).toBe(false)
  })

  test('refreshes once on 401 then retries', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED' })).mockResolvedValueOnce(json(200, { id: 1 }))
    auth.refreshSession.mockResolvedValue(session('fresh'))

    const body = await api<{ id: number }>('/me')

    expect(body).toEqual({ id: 1 })
    expect(auth.refreshSession).toHaveBeenCalledTimes(1)
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get('Authorization')).toBe('Bearer fresh')
  })

  test('signs out and redirects when refresh fails', async () => {
    window.history.pushState({}, '', '/account?tab=profile')
    fetchMock.mockResolvedValue(json(401, { code: 'UNAUTHENTICATED' }))
    auth.refreshSession.mockResolvedValue({ data: { session: null }, error: new Error('expired') })

    await expect(api('/me')).rejects.toMatchObject({ status: 401 })

    expect(auth.signOut).toHaveBeenCalled()
    expect(nav.redirectToLogin).toHaveBeenCalledWith('/account?tab=profile')
  })

  test('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(api('/saved/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  test('network failure becomes ApiError with status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(api('/me')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })
})
