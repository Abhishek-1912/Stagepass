import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const manage = vi.hoisted(() => ({ useRefundRequests: vi.fn(), useApproveRefund: vi.fn(), useDeclineRefund: vi.fn() }))
vi.mock('./api', () => manage)
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('sonner', () => ({ toast }))

import { RefundsTab } from './RefundsTab'

const approve = vi.fn()
const decline = vi.fn()
const request = {
  id: 'r1',
  bookingId: 'b1',
  bookingCode: 'SP-ABC234',
  attendeeName: 'Asha Rao',
  attendeeEmail: 'asha@test.in',
  kind: 'ATTENDEE_REQUEST',
  status: 'PENDING_APPROVAL',
  ticketCount: 1,
  amountPaise: 49900,
  reason: 'Exam that day',
  note: null,
  createdAt: '2026-11-05T10:00:00Z',
}

beforeEach(() => {
  approve.mockReset().mockResolvedValue({ ...request, status: 'PROCESSING' })
  decline.mockReset().mockResolvedValue({ ...request, status: 'DECLINED' })
  manage.useRefundRequests.mockReturnValue({
    data: [request, { ...request, id: 'r2', bookingCode: 'SP-XYZ789', status: 'DECLINED', reason: null, note: 'Too late' }],
    isPending: false,
  })
  manage.useApproveRefund.mockReturnValue({ mutateAsync: approve, isPending: false })
  manage.useDeclineRefund.mockReturnValue({ mutateAsync: decline, isPending: false })
})

test('approving a refund confirms the amount first', async () => {
  render(<RefundsTab eventId="e1" />)
  expect(screen.getByText('Exam that day')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: 'Approve' }))
  expect(screen.getByRole('dialog')).toHaveTextContent('₹499.00')
  await userEvent.click(screen.getByRole('button', { name: 'Approve refund' }))

  expect(approve).toHaveBeenCalledWith('r1')
  expect(toast.success).toHaveBeenCalled()
})

test('declining sends the note', async () => {
  render(<RefundsTab eventId="e1" />)
  await userEvent.click(screen.getByRole('button', { name: 'Decline' }))
  await userEvent.type(screen.getByRole('textbox'), 'Too close to the show')
  await userEvent.click(screen.getByRole('button', { name: 'Decline request' }))

  expect(decline).toHaveBeenCalledWith({ id: 'r1', note: 'Too close to the show' })
})

test('decided requests show their status and note', () => {
  render(<RefundsTab eventId="e1" />)
  expect(screen.getByText('Declined')).toBeInTheDocument()
  expect(screen.getByText(/Too late/)).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: 'Approve' })).toHaveLength(1)
})
