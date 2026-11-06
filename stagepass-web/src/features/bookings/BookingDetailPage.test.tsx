import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import type { Booking } from '@/features/checkout/types'
import { refundInfo, testBooking } from './testBooking'

const api = vi.hoisted(() => ({ useMyBooking: vi.fn(), useRequestRefund: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async () => 'data:image/png;base64,QR') } }))
const downloads = vi.hoisted(() => ({ downloadFile: vi.fn(async () => {}) }))
vi.mock('./downloads', () => downloads)

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

test('booking detail links each live ticket and offers PDF and invoice downloads', async () => {
  renderAt(
    testBooking({
      tickets: [
        { id: 'k1', code: 'SP-AAAA-2222', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'VALID', qrToken: 'tok1.sig' },
        { id: 'k2', code: 'SP-BBBB-3333', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'REFUNDED' },
      ],
    }),
  )

  expect(await screen.findByRole('img', { name: 'QR code for SP-AAAA-2222' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /show ticket SP-AAAA-2222/i })).toHaveAttribute('href', '/account/bookings/b1/tickets/k1')
  expect(screen.queryByRole('link', { name: /show ticket SP-BBBB-3333/i })).not.toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: /download tickets/i }))
  expect(downloads.downloadFile).toHaveBeenCalledWith('/me/bookings/b1/tickets.pdf', 'stagepass-SP-ABC234.pdf')
  await userEvent.click(screen.getByRole('button', { name: /download invoice/i }))
  expect(downloads.downloadFile).toHaveBeenCalledWith('/me/bookings/b1/invoice.pdf', 'stagepass-invoice.pdf')
})
