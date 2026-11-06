import { afterEach, beforeEach, expect, test, vi } from 'vitest'

const auth = vi.hoisted(() => ({ getSession: vi.fn(), refreshSession: vi.fn(), signOut: vi.fn() }))
vi.mock('@/lib/supabase', () => ({ supabase: { auth } }))
vi.mock('@/lib/navigation', () => ({ redirectToLogin: vi.fn() }))

import { ApiError } from '@/lib/api/ApiError'
import { downloadFile } from './downloads'

const fetchMock = vi.fn()
let clicked: { download: string; href: string } | null = null

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  auth.getSession.mockResolvedValue({ data: { session: { access_token: 'tok' } }, error: null })
  URL.createObjectURL = vi.fn(() => 'blob:mock')
  URL.revokeObjectURL = vi.fn()
  clicked = null
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    clicked = { download: this.download, href: this.href }
  })
})
afterEach(() => vi.restoreAllMocks())

test('downloadFile saves with the server filename', async () => {
  fetchMock.mockResolvedValue(
    new Response(new Blob(['%PDF-1.4']), {
      status: 200,
      headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="stagepass-SP-ABC234.pdf"' },
    }),
  )

  await downloadFile('/me/bookings/b1/tickets.pdf', 'tickets.pdf')

  expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/v1\/me\/bookings\/b1\/tickets\.pdf$/)
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer tok')
  expect(clicked).toEqual({ download: 'stagepass-SP-ABC234.pdf', href: 'blob:mock' })
})

test('downloadFile falls back to the given name', async () => {
  fetchMock.mockResolvedValue(new Response(new Blob(['x']), { status: 200 }))
  await downloadFile('/me/bookings/b1/invoice.pdf', 'invoice.pdf')
  expect(clicked?.download).toBe('invoice.pdf')
})

test('downloadFile turns problem responses into ApiError', async () => {
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ status: 409, code: 'INVALID_STATE', detail: 'No valid tickets' }), {
      status: 409,
      headers: { 'Content-Type': 'application/problem+json' },
    }),
  )

  const error = await downloadFile('/me/bookings/b1/tickets.pdf', 'x.pdf').catch((e: unknown) => e)

  expect(error).toBeInstanceOf(ApiError)
  expect((error as ApiError).code).toBe('INVALID_STATE')
  expect(clicked).toBeNull()
})
