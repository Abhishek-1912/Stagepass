import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { publicEvent } from '@/features/event/testDetail'
import { ApiError } from '@/lib/api/ApiError'
import type { Booking } from './types'

const api = vi.hoisted(() => ({
  useQuote: vi.fn(),
  useCreateBooking: vi.fn(),
  useVerifyPayment: vi.fn(),
  useMyBooking: vi.fn(),
}))
vi.mock('./api', () => api)
const discovery = vi.hoisted(() => ({ usePublicEvent: vi.fn() }))
vi.mock('@/features/discovery/api', () => discovery)
const me = vi.hoisted(() => ({ useMe: vi.fn() }))
vi.mock('@/features/auth/useMe', () => me)
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('sonner', () => ({ toast }))
const razorpay = vi.hoisted(() => ({ openRazorpay: vi.fn() }))
vi.mock('./razorpay', async (orig) => ({ ...(await orig<typeof import('./razorpay')>()), openRazorpay: razorpay.openRazorpay }))

import { CheckoutPage } from './CheckoutPage'
import { CheckoutDismissed } from './razorpay'

const TYPE = 'aaaaaaaa-0000-0000-0000-000000000001'
const price = { subtotalPaise: 99800, feePaise: 4495, feeGstPaise: 809, totalPaise: 105104, commissionPaise: 0, ticketCount: 2 }
const lines = [{ ticketTypeId: TYPE, name: 'General', quantity: 2, unitPricePaise: 49900, amountPaise: 99800 }]

function booking(overrides: Partial<Booking> = {}): Booking {
  return {
    id: 'b1',
    code: 'SP-ABC234',
    status: 'PENDING_PAYMENT',
    eventId: 'e1',
    eventTitle: 'Sunburn Goa',
    eventSlug: 'sunburn-goa-abc123',
    eventStartsAt: '2026-12-20T13:30:00Z',
    items: lines,
    price,
    holdExpiresAt: new Date(Date.now() + 600_000).toISOString(),
    checkout: { razorpayKeyId: 'rzp_test_key', razorpayOrderId: 'order_1', amountPaise: 105104, currency: 'INR', companyName: 'Stagepass' },
    tickets: [],
    attendeeName: 'Asha Rao',
    attendeeEmail: 'asha@test.in',
    ...overrides,
  }
}

const create = vi.fn()
const verify = vi.fn()
beforeEach(() => {
  discovery.usePublicEvent.mockReturnValue({ data: publicEvent(), isPending: false })
  api.useQuote.mockReturnValue({ data: { lines, price, holdMinutes: 10 }, isPending: false, error: null })
  me.useMe.mockReturnValue({ data: { id: 'u1', email: 'asha@test.in', fullName: 'Asha Rao', phone: null, roles: ['ATTENDEE'] } })
  create.mockReset()
  verify.mockReset()
  api.useCreateBooking.mockReturnValue({ mutateAsync: create, isPending: false })
  api.useVerifyPayment.mockReturnValue({ mutateAsync: verify, isPending: false })
  razorpay.openRazorpay.mockReset()
  toast.error.mockReset()
})
afterEach(() => sessionStorage.clear())

function Where() {
  const l = useLocation()
  return <p data-testid="where">{l.pathname + l.search}</p>
}

function renderCheckout(items = `${TYPE}:2`) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/checkout/sunburn-goa-abc123?items=${items}`]}>
        <Routes>
          <Route path="/checkout/:slug" element={<CheckoutPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

test('checkout shows the server price breakdown', () => {
  renderCheckout()
  expect(screen.getByText('Convenience fee').nextSibling).toHaveTextContent('₹44.95')
  expect(screen.getByText('GST on fee').nextSibling).toHaveTextContent('₹8.09')
  expect(screen.getByTestId('price-total')).toHaveTextContent('₹1,051.04')
  expect(screen.getByRole('button', { name: 'Pay ₹1,051.04' })).toBeEnabled()
  expect(screen.getByLabelText('Full name')).toHaveValue('Asha Rao')
})

test('pay creates booking with idempotency key then verifies', async () => {
  create.mockResolvedValue(booking())
  razorpay.openRazorpay.mockResolvedValue({ razorpayOrderId: 'order_1', razorpayPaymentId: 'pay_1', razorpaySignature: 'sig' })
  verify.mockResolvedValue(booking({ status: 'CONFIRMED' }))
  renderCheckout()

  await userEvent.selectOptions(screen.getByLabelText(/state/i), 'Telangana')
  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))

  expect(create).toHaveBeenCalledTimes(1)
  const { key, body } = create.mock.calls[0][0]
  expect(key).toHaveLength(36)
  expect(body).toEqual({
    eventId: 'e1',
    items: [{ ticketTypeId: TYPE, quantity: 2 }],
    attendeeName: 'Asha Rao',
    attendeeEmail: 'asha@test.in',
    attendeePhone: undefined,
    buyerState: 'Telangana',
  })
  expect(razorpay.openRazorpay).toHaveBeenCalledWith(booking().checkout, expect.objectContaining({ email: 'asha@test.in' }), 'Sunburn Goa')
  expect(verify).toHaveBeenCalledWith({ bookingId: 'b1', razorpayOrderId: 'order_1', razorpayPaymentId: 'pay_1', razorpaySignature: 'sig' })
  expect(await screen.findByTestId('where')).toHaveTextContent('/account/bookings/b1?status=success')
})

test('dismissing Razorpay keeps the booking resumable', async () => {
  create.mockResolvedValue(booking())
  razorpay.openRazorpay.mockRejectedValue(new CheckoutDismissed(null))
  renderCheckout()

  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))

  expect(await screen.findByTestId('where')).toHaveTextContent('/checkout/booking/b1')
  expect(verify).not.toHaveBeenCalled()
})

test('a second click reuses the same idempotency key', async () => {
  create.mockResolvedValue(booking())
  razorpay.openRazorpay.mockRejectedValue(new CheckoutDismissed(null))
  const { unmount } = renderCheckout()
  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))
  unmount()
  renderCheckout()
  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))

  expect(create.mock.calls[1][0].key).toBe(create.mock.calls[0][0].key)
})

test('free order skips Razorpay', async () => {
  api.useQuote.mockReturnValue({
    data: { lines, price: { ...price, feePaise: 0, feeGstPaise: 0, subtotalPaise: 0, totalPaise: 0 }, holdMinutes: 10 },
    isPending: false,
    error: null,
  })
  create.mockResolvedValue(booking({ status: 'CONFIRMED', checkout: undefined }))
  renderCheckout()

  await userEvent.click(screen.getByRole('button', { name: 'Get free tickets' }))

  expect(razorpay.openRazorpay).not.toHaveBeenCalled()
  expect(await screen.findByTestId('where')).toHaveTextContent('/account/bookings/b1?status=success')
})

test('sold out on create shows a toast', async () => {
  create.mockRejectedValue(new ApiError(409, 'SOLD_OUT', 'Not enough General tickets left'))
  renderCheckout()

  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))

  expect(toast.error).toHaveBeenCalledWith('Not enough General tickets left')
  expect(razorpay.openRazorpay).not.toHaveBeenCalled()
})

test('invalid attendee details are shown inline and nothing is created', async () => {
  me.useMe.mockReturnValue({ data: undefined })
  renderCheckout()

  await userEvent.type(screen.getByLabelText(/mobile/i), '12345')
  await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,051.04' }))

  expect(screen.getByText(/enter the attendee/i)).toBeInTheDocument()
  expect(screen.getByText(/valid email/i)).toBeInTheDocument()
  expect(screen.getByText(/10-digit/i)).toBeInTheDocument()
  expect(create).not.toHaveBeenCalled()
})
