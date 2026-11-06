import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useMyBookings: vi.fn() }))
vi.mock('./api', () => api)

import { MyBookingsPage } from './MyBookingsPage'

const page = (items: unknown[]) => ({ data: { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }, isPending: false, error: null })

beforeEach(() => api.useMyBookings.mockReset())

test('lists upcoming bookings with status and total', async () => {
  api.useMyBookings.mockImplementation((scope: string) =>
    scope === 'upcoming'
      ? page([
          { id: 'b1', code: 'SP-ABC234', status: 'CONFIRMED', eventTitle: 'Sunburn Goa', eventSlug: 's', coverImageUrl: null, eventStartsAt: '2026-12-20T13:30:00Z', city: 'Goa', ticketCount: 2, totalPaise: 105104 },
          { id: 'b2', code: 'SP-XYZ789', status: 'PENDING_PAYMENT', eventTitle: 'Comedy Night', eventSlug: 'c', coverImageUrl: null, eventStartsAt: '2026-12-22T13:30:00Z', city: 'Pune', ticketCount: 1, totalPaise: 0 },
        ])
      : page([]),
  )
  render(
    <MemoryRouter>
      <MyBookingsPage />
    </MemoryRouter>,
  )

  const cards = screen.getAllByRole('link', { name: /sunburn goa|comedy night/i })
  expect(cards).toHaveLength(2)
  expect(cards[0]).toHaveAttribute('href', '/account/bookings/b1')
  expect(within(cards[0]).getByText('Confirmed')).toBeInTheDocument()
  expect(within(cards[0]).getByText('₹1,051.04')).toBeInTheDocument()
  expect(within(cards[0]).getByText(/2 tickets/)).toBeInTheDocument()
  expect(within(cards[1]).getByText('Payment pending')).toBeInTheDocument()
  expect(within(cards[1]).getByText('Free')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('tab', { name: 'Past' }))
  expect(api.useMyBookings).toHaveBeenLastCalledWith('past')
  expect(screen.getByRole('link', { name: /explore events/i })).toBeInTheDocument()
})
