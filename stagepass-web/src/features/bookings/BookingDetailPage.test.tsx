import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import type { Booking } from '@/features/checkout/types'
import { refundInfo, testBooking } from './testBooking'

const api = vi.hoisted(() => ({ useMyBooking: vi.fn(), useRequestRefund: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { BookingDetailPage } from './BookingDetailPage'

beforeEach(() => api.useRequestRefund.mockReturnValue({ mutateAsync: vi.fn(), isPending: false }))

function renderAt(booking: Booking, search = '') {
  api.useMyBooking.mockReturnValue({ data: booking, isPending: false, error: null })
  return render(
    <MemoryRouter initialEntries={[`/account/bookings/b1${search}`]}>
      <Routes>
        <Route path="/account/bookings/:id" element={<BookingDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('success banner after payment', () => {
  renderAt(testBooking(), '?status=success')
  expect(screen.getByText(/you’re going! booking sp-abc234 is confirmed/i)).toBeInTheDocument()
  expect(screen.getByText('SP-AAAA-2222')).toBeInTheDocument()
  expect(screen.getByText(/qr codes for entry are coming soon/i)).toBeInTheDocument()
  expect(screen.getByText('GST invoice SP/2026-27/000042')).toBeInTheDocument()
  expect(api.useMyBooking).toHaveBeenCalledWith('b1', { pollWhilePending: true })
})

test('pending booking offers to complete payment', () => {
  renderAt(
    testBooking({
      status: 'PENDING_PAYMENT',
      tickets: [],
      refund: undefined,
      holdExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
      checkout: { razorpayKeyId: 'k', razorpayOrderId: 'order_1', amountPaise: 105104, currency: 'INR', companyName: 'Stagepass' },
    }),
  )
  expect(screen.getByRole('link', { name: 'Complete payment' })).toHaveAttribute('href', '/checkout/booking/b1')
  expect(screen.getByRole('timer')).toHaveTextContent(/tickets held for [45]:\d\d/i)
})

test('outside policy shows request-to-organizer wording', () => {
  renderAt(
    testBooking({
      refund: refundInfo({
        selfServe: false,
        kind: 'NONE',
        message: 'Outside the refund policy - the organizer will review your request.',
        history: [{ id: 'r1', status: 'DECLINED', kind: 'ATTENDEE_REQUEST', amountPaise: 49900, ticketCount: 1, ticketIds: ['k1'], note: 'Too close to the show', createdAt: '2026-11-06T10:00:00Z' }],
      }),
    }),
  )
  expect(screen.getByRole('button', { name: 'Request a refund' })).toBeInTheDocument()
  expect(screen.getByText(/organizer will review/i)).toBeInTheDocument()
  expect(screen.getByText('Declined')).toBeInTheDocument()
  expect(screen.getByText('Organizer: Too close to the show')).toBeInTheDocument()
})
