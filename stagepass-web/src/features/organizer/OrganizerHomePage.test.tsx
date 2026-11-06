import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const dashboard = vi.hoisted(() => ({ useOverview: vi.fn() }))
vi.mock('./dashboard/api', () => dashboard)

import { OrganizerHomePage } from './OrganizerHomePage'

const overview = {
  liveEvents: 2,
  upcomingEvents: 1,
  ticketsSold: 3,
  grossSalesPaise: 149700,
  netSalesPaise: 99800,
  earningsPaise: 94810,
  recentBookings: [
    { id: 'b1', eventId: 'e1', code: 'SP-ABC234', eventTitle: 'Sunburn Goa', attendeeName: 'Asha Rao', ticketCount: 2, totalPaise: 105104, confirmedAt: '2026-11-05T10:00:00Z' },
  ],
  dailySales: [{ date: '2026-11-05', tickets: 2, amountPaise: 99800 }],
}

beforeEach(() => dashboard.useOverview.mockReset().mockReturnValue({ data: overview, isPending: false, error: null }))

function renderPage() {
  return render(
    <MemoryRouter>
      <OrganizerHomePage />
    </MemoryRouter>,
  )
}

test('overview shows KPIs and recent bookings', () => {
  renderPage()
  expect(within(screen.getByText('Net sales').closest('div')!.parentElement!).getByText('₹998')).toBeInTheDocument()
  expect(within(screen.getByText('Tickets sold').closest('div')!.parentElement!).getByText('3')).toBeInTheDocument()
  expect(within(screen.getByText('Earnings').closest('div')!.parentElement!).getByText('₹948.10')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /SP-ABC234/ })).toHaveAttribute('href', '/organizer/events/e1/manage?tab=bookings')
  expect(screen.getByRole('link', { name: /create event/i })).toHaveAttribute('href', '/organizer/events/new')
})

test('range picker refetches with the new range', async () => {
  renderPage()
  expect(dashboard.useOverview).toHaveBeenLastCalledWith(30)
  await userEvent.click(screen.getByRole('radio', { name: '7 days' }))
  expect(dashboard.useOverview).toHaveBeenLastCalledWith(7)
})

test('no bookings yet shows a friendly empty state', () => {
  dashboard.useOverview.mockReturnValue({ data: { ...overview, recentBookings: [] }, isPending: false, error: null })
  renderPage()
  expect(screen.getByText(/no bookings yet/i)).toBeInTheDocument()
})
