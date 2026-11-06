import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { expect, test, vi } from 'vitest'
import { testBooking } from '@/features/bookings/testBooking'

const api = vi.hoisted(() => ({ useMyBooking: vi.fn(), useVerifyPayment: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })) }))
vi.mock('./api', () => api)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { ResumeCheckoutPage } from './CheckoutPage'

function renderWith(booking: ReturnType<typeof testBooking>) {
  api.useMyBooking.mockReturnValue({ data: booking, isPending: false, error: null })
  return render(
    <MemoryRouter initialEntries={['/checkout/booking/b1']}>
      <Routes>
        <Route path="/checkout/booking/:bookingId" element={<ResumeCheckoutPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const pending = { status: 'PENDING_PAYMENT' as const, tickets: [], refund: undefined, holdExpiresAt: new Date(Date.now() + 300_000).toISOString() }

test('waits for the payment order instead of showing a dead Pay button', () => {
  renderWith(testBooking({ ...pending, checkout: undefined }))
  expect(screen.getByText('Preparing payment…')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /pay/i })).not.toBeInTheDocument()
  expect(api.useMyBooking).toHaveBeenCalledWith('b1', { pollWhilePending: true })
})

test('resumes payment while the hold lasts and says when it expired', () => {
  const checkout = { razorpayKeyId: 'k', razorpayOrderId: 'order_1', amountPaise: 105104, currency: 'INR', companyName: 'Stagepass' }
  const { unmount } = renderWith(testBooking({ ...pending, checkout }))
  expect(screen.getByRole('button', { name: 'Pay ₹1,051.04' })).toBeEnabled()
  expect(screen.getByRole('timer')).toBeInTheDocument()
  unmount()
  renderWith(testBooking({ ...pending, status: 'EXPIRED', checkout }))
  expect(screen.getByText(/hold expired/i)).toBeInTheDocument()
})
