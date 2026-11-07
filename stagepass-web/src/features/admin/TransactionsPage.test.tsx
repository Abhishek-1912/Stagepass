import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({
  useAdminPayments: vi.fn(),
  useAdminRefunds: vi.fn(),
  useAdminDisputes: vi.fn(),
  useAdminRefund: vi.fn(),
  useRetryRefund: vi.fn(),
  query: (p: Record<string, unknown>) => {
    const q = new URLSearchParams()
    Object.entries(p).forEach(([k, v]) => v !== undefined && v !== '' && q.set(k, String(v)))
    return q.toString() ? `?${q}` : ''
  },
}))
vi.mock('./api', () => hooks)
const files = vi.hoisted(() => ({ downloadFile: vi.fn() }))
vi.mock('@/features/bookings/downloads', () => files)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { TransactionsPage } from './TransactionsPage'

const payment = {
  id: 'p1',
  bookingId: 'b1',
  bookingCode: 'SP-7K2M9Q',
  eventTitle: 'Sunburn Goa',
  attendeeEmail: 'asha@stagepass.test',
  amountPaise: 105104,
  status: 'CAPTURED',
  method: 'upi',
  razorpayPaymentId: 'pay_123',
  gatewayFeePaise: 2000,
  createdAt: '2026-12-01T10:00:00Z',
}
const refund = vi.fn()
const empty = { data: { items: [], totalItems: 0, totalPages: 0, page: 0 }, isPending: false }

beforeEach(() => {
  refund.mockReset().mockResolvedValue({})
  files.downloadFile.mockReset().mockResolvedValue(undefined)
  hooks.useAdminPayments.mockReturnValue({ data: { items: [payment], totalItems: 1, totalPages: 1, page: 0 }, isPending: false })
  hooks.useAdminRefunds.mockReturnValue(empty)
  hooks.useAdminDisputes.mockReturnValue(empty)
  hooks.useAdminRefund.mockReturnValue({ mutateAsync: refund, isPending: false })
  hooks.useRetryRefund.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <TransactionsPage />
    </MemoryRouter>,
  )
}

test('refund dialog sends includeFee and reason', async () => {
  renderPage()
  const row = screen.getByRole('row', { name: /SP-7K2M9Q/ })
  await userEvent.click(within(row).getByRole('button', { name: /refund/i }))
  const fee = screen.getByRole('checkbox', { name: /convenience fee/i })
  expect(fee).toBeChecked()
  await userEvent.click(fee)
  await userEvent.type(screen.getByLabelText(/reason/i), 'Customer complaint')
  await userEvent.click(screen.getByRole('button', { name: 'Refund booking' }))
  expect(refund).toHaveBeenCalledWith({ bookingId: 'b1', includeFee: false, reason: 'Customer complaint' })
})

test('csv export downloads with the date range', async () => {
  renderPage()
  await userEvent.type(screen.getByLabelText('From'), '2026-12-01')
  await userEvent.type(screen.getByLabelText('To'), '2026-12-31')
  await userEvent.click(screen.getByRole('button', { name: /export csv/i }))
  expect(files.downloadFile).toHaveBeenCalledWith('/admin/payments/export?from=2026-12-01&to=2026-12-31', 'stagepass-payments.csv')
})

test('export needs both dates', () => {
  renderPage()
  expect(screen.getByRole('button', { name: /export csv/i })).toBeDisabled()
})
