import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { Button } from '@/components/ui/button'
import { RefundDialog } from './RefundDialog'
import { estimateRefund } from './refundEstimate'
import { refundInfo, testBooking } from './testBooking'

const api = vi.hoisted(() => ({ useRequestRefund: vi.fn() }))
vi.mock('./api', () => api)
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('sonner', () => ({ toast }))

const mutate = vi.fn()
beforeEach(() => {
  mutate.mockReset().mockResolvedValue(testBooking())
  api.useRequestRefund.mockReturnValue({ mutateAsync: mutate, isPending: false })
})

test('self-serve refund shows the amount and whether the fee is included', async () => {
  render(<RefundDialog booking={testBooking()} trigger={<Button>Refund tickets</Button>} />)
  await userEvent.click(screen.getByRole('button', { name: 'Refund tickets' }))

  expect(screen.getByTestId('refund-estimate')).toHaveTextContent('₹998.00')
  expect(screen.getByTestId('refund-estimate')).toHaveTextContent(/convenience fee isn’t refunded/i)

  await userEvent.click(screen.getByRole('checkbox', { name: /SP-BBBB-3333/ }))
  expect(screen.getByTestId('refund-estimate')).toHaveTextContent('₹499.00')

  await userEvent.click(screen.getByRole('button', { name: 'Refund now' }))
  expect(mutate).toHaveBeenCalledWith({ ticketIds: ['k1'], reason: undefined })
  expect(toast.success).toHaveBeenCalled()
})

test('reschedule refunds include the fee share', () => {
  const b = testBooking({ refund: refundInfo({ kind: 'RESCHEDULE', includesFee: true }) })
  expect(estimateRefund(b, ['k1', 'k2'], true)).toBe(105104)
  expect(estimateRefund(b, ['k1'], true)).toBe(52552)
})

test('tickets already waiting on a request are not offered again', async () => {
  const b = testBooking({
    refund: refundInfo({
      selfServe: false,
      history: [{ id: 'r1', status: 'PENDING_APPROVAL', kind: 'ATTENDEE_REQUEST', amountPaise: 49900, ticketCount: 1, ticketIds: ['k1'], note: null, createdAt: '2026-11-06T10:00:00Z' }],
    }),
  })
  render(<RefundDialog booking={b} trigger={<Button>Request a refund</Button>} />)
  await userEvent.click(screen.getByRole('button', { name: 'Request a refund' }))

  expect(screen.queryByRole('checkbox', { name: /SP-AAAA-2222/ })).not.toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: /SP-BBBB-3333/ })).toBeChecked()
  expect(screen.getByRole('button', { name: 'Send request' })).toBeEnabled()
  expect(screen.getByTestId('refund-estimate')).toHaveTextContent(/if approved/i)
})
