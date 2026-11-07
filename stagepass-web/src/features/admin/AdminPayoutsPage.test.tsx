import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const hooks = vi.hoisted(() => ({ useAdminPayouts: vi.fn(), useRecordPayout: vi.fn() }))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { AdminPayoutsPage } from './AdminPayoutsPage'

const summary = {
  organizerId: 'o1',
  orgName: 'Acme Live',
  status: 'APPROVED',
  pendingPaise: 10000,
  payablePaise: 50000,
  paidOutPaise: 0,
  negative: false,
  lastPayoutAt: null,
}
const record = vi.fn()

beforeEach(() => {
  record.mockReset()
  hooks.useAdminPayouts.mockReturnValue({ data: { items: [summary], totalItems: 1, totalPages: 1, page: 0 }, isPending: false })
  hooks.useRecordPayout.mockReturnValue({ mutateAsync: record, isPending: false })
})

test('record payout sends paise and shows the over-balance error', async () => {
  record.mockRejectedValueOnce(
    new ApiError(400, 'VALIDATION_FAILED', 'Request validation failed', [
      { field: 'amountPaise', message: 'is more than the payable balance (Rs. 500.00)' },
    ]),
  )
  render(<AdminPayoutsPage />)
  const row = screen.getByRole('row', { name: /acme live/i })
  await userEvent.click(within(row).getByRole('button', { name: /record payout/i }))

  const amount = screen.getByLabelText(/amount/i)
  expect(amount).toHaveValue('500')
  await userEvent.clear(amount)
  await userEvent.type(amount, '200.50')
  await userEvent.type(screen.getByLabelText(/reference/i), 'UTR998877')
  await userEvent.click(screen.getByRole('button', { name: 'Save payout' }))

  expect(record).toHaveBeenCalledWith({
    organizerId: 'o1',
    input: expect.objectContaining({ amountPaise: 20050, reference: 'UTR998877', paidOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) }),
  })
  expect(await screen.findByText('is more than the payable balance (Rs. 500.00)')).toBeInTheDocument()
})

test('bad amount is caught before sending', async () => {
  render(<AdminPayoutsPage />)
  await userEvent.click(screen.getByRole('button', { name: /record payout/i }))
  const amount = screen.getByLabelText(/amount/i)
  await userEvent.clear(amount)
  await userEvent.type(amount, '12.345')
  await userEvent.type(screen.getByLabelText(/reference/i), 'UTR1')
  await userEvent.click(screen.getByRole('button', { name: 'Save payout' }))
  expect(record).not.toHaveBeenCalled()
  expect(screen.getByText(/up to 2 decimals/i)).toBeInTheDocument()
})
