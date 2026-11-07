import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({ useAdminOverview: vi.fn() }))
vi.mock('./api', () => hooks)

import { OverviewPage } from './OverviewPage'

const overview = {
  days: 30,
  gmvPaise: 12345000,
  platformRevenuePaise: 250050,
  refundsPaise: 49900,
  bookings: 41,
  ticketsSold: 77,
  newUsers: 12,
  totalUsers: 340,
  liveEvents: 9,
  pendingApplications: 2,
  pendingEvents: 4,
  openReports: 3,
  failedRefunds: 1,
  openDisputes: 0,
  daily: [{ date: '2026-11-07', tickets: 3, amountPaise: 150000 }],
}

beforeEach(() => {
  hooks.useAdminOverview.mockReturnValue({ data: overview, isPending: false, isError: false })
})

test('overview shows money and attention counts', () => {
  render(
    <MemoryRouter>
      <OverviewPage />
    </MemoryRouter>,
  )
  expect(screen.getByText('₹1,23,450')).toBeInTheDocument()
  expect(screen.getByText('₹2,500.50')).toBeInTheDocument()
  expect(screen.getByText('77')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /3 open reports/i })).toHaveAttribute('href', '/admin/reports')
  expect(screen.getByRole('link', { name: /4 events to review/i })).toHaveAttribute('href', '/admin/events')
  expect(screen.getByRole('link', { name: /1 failed refund/i })).toHaveAttribute('href', '/admin/transactions?tab=refunds')
})

test('overview error state', () => {
  hooks.useAdminOverview.mockReturnValue({ data: undefined, isPending: false, isError: true })
  render(
    <MemoryRouter>
      <OverviewPage />
    </MemoryRouter>,
  )
  expect(screen.getByText(/couldn.t load the overview/i)).toBeInTheDocument()
})
