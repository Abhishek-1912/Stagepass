import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const manage = vi.hoisted(() => ({
  useEventBookings: vi.fn(),
  useEventAttendees: vi.fn(),
  useRefundRequests: vi.fn(),
  useApproveRefund: vi.fn(),
  useDeclineRefund: vi.fn(),
}))
vi.mock('./api', () => manage)
const dashboard = vi.hoisted(() => ({ useEventStats: vi.fn() }))
vi.mock('../dashboard/api', () => dashboard)
const organizer = vi.hoisted(() => ({ useOrganizerEvent: vi.fn() }))
vi.mock('../api', () => organizer)
vi.mock('@/features/bookings/downloads', () => ({ downloadFile: vi.fn() }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/features/checkin/api', () => ({
  useCheckInStats: () => ({ data: { checkedIn: 1, total: 3, byGate: [], byScanner: [], lastScanAt: null }, isPending: false }),
  useGates: () => ({ data: [], isPending: false }),
  useCreateGate: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateGate: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteGate: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useScanLog: () => ({ data: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }, isPending: false }),
}))

import { EventManagePage } from './EventManagePage'

const page = (items: unknown[]) => ({ data: { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }, isPending: false })

beforeEach(() => {
  organizer.useOrganizerEvent.mockReturnValue({ data: { id: 'e1', title: 'Sunburn Goa', status: 'PUBLISHED', slug: 'sunburn-goa', startsAt: '2026-12-20T13:30:00Z' }, isPending: false })
  dashboard.useEventStats.mockReturnValue({
    data: { capacity: 150, ticketsSold: 3, held: 1, checkedIn: 1, grossSalesPaise: 149700, refundedPaise: 49900, netSalesPaise: 99800, commissionPaise: 0, byType: [], dailySales: [] },
    isPending: false,
  })
  manage.useEventBookings.mockReturnValue(page([]))
  manage.useEventAttendees.mockReturnValue(page([]))
  manage.useRefundRequests.mockReturnValue({ data: [], isPending: false })
  manage.useApproveRefund.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  manage.useDeclineRefund.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

function Where() {
  const l = useLocation()
  return <p data-testid="where">{l.search}</p>
}

function renderAt(search = '') {
  return render(
    <MemoryRouter initialEntries={[`/organizer/events/e1/manage${search}`]}>
      <Routes>
        <Route
          path="/organizer/events/:id/manage"
          element={
            <>
              <EventManagePage />
              <Where />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

test('defaults to sales with the event KPIs', () => {
  renderAt()
  expect(screen.getByRole('heading', { name: 'Sunburn Goa' })).toBeInTheDocument()
  expect(screen.getByRole('tab', { name: 'Sales', selected: true })).toBeInTheDocument()
  expect(screen.getByText('3 / 150')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /edit event/i })).toHaveAttribute('href', '/organizer/events/e1/review')
})

test('tabs follow the URL', async () => {
  renderAt('?tab=attendees')
  expect(screen.getByRole('tab', { name: 'Attendees', selected: true })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /export csv/i })).toBeInTheDocument()

  await userEvent.click(screen.getByRole('tab', { name: 'Refunds' }))
  expect(screen.getByTestId('where')).toHaveTextContent('?tab=refunds')
  expect(screen.getByText(/no refund requests/i)).toBeInTheDocument()
})

test('check-in tab shows progress and opens the scanner for this event', () => {
  renderAt('?tab=checkin')
  expect(screen.getByText('1 of 3 checked in')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /open scanner/i })).toHaveAttribute('href', '/scan/e1')
})
