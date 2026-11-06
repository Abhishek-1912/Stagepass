import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { expect, test, vi } from 'vitest'
import { testBooking } from './testBooking'

const api = vi.hoisted(() => ({ useMyBooking: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async () => 'data:image/png;base64,QR') } }))

import { TicketViewPage } from './TicketViewPage'

function renderAt(ticketId: string, booking = testBooking()) {
  api.useMyBooking.mockReturnValue({ data: booking, isPending: false, error: null })
  return render(
    <MemoryRouter initialEntries={[`/account/bookings/b1/tickets/${ticketId}`]}>
      <Routes>
        <Route path="/account/bookings/:id/tickets/:ticketId" element={<TicketViewPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const live = testBooking({
  tickets: [
    { id: 'k1', code: 'SP-AAAA-2222', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'VALID', qrToken: 'tok1.sig' },
    { id: 'k2', code: 'SP-BBBB-3333', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'REFUNDED' },
  ],
})

test('ticket view shows a live ticket large and hides QR for refunded ones', async () => {
  renderAt('k1', live)
  expect(await screen.findByRole('img', { name: 'QR code for SP-AAAA-2222' })).toBeInTheDocument()
  expect(screen.getByText('Sunburn Goa')).toBeInTheDocument()
  expect(screen.getByText(/turn your screen brightness up/i)).toBeInTheDocument()
  expect(screen.getByText('1 of 2')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('link', { name: /next ticket/i }))
  expect(screen.getByText('SP-BBBB-3333')).toBeInTheDocument()
  expect(screen.queryByRole('img', { name: /qr code/i })).not.toBeInTheDocument()
  expect(screen.getByText('Refunded')).toBeInTheDocument()
})

test('unknown ticket says so', () => {
  renderAt('nope', live)
  expect(screen.getByText(/couldn’t find this ticket/i)).toBeInTheDocument()
})
