import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, test, vi } from 'vitest'

const checkin = vi.hoisted(() => ({
  useCheckInStats: vi.fn(),
  useGates: vi.fn(() => ({ data: [], isPending: false })),
  useCreateGate: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
  useUpdateGate: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
  useDeleteGate: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
  useScanLog: vi.fn(() => ({
    data: { items: [{ at: '2026-11-07T14:00:00Z', result: 'INVALID', ticketCode: null, holderName: null, gateName: 'Gate 1', scannerName: 'Ravi', manual: false }], page: 0, size: 20, totalItems: 1, totalPages: 1 },
    isPending: false,
  })),
}))
vi.mock('@/features/checkin/api', () => checkin)
const dashboard = vi.hoisted(() => ({ useEventStats: vi.fn(() => ({ data: { byType: [] }, isPending: false })) }))
vi.mock('../dashboard/api', () => dashboard)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { CheckInTab } from './CheckInTab'

test('check-in tab shows per-gate counts and polls', () => {
  checkin.useCheckInStats.mockReturnValue({
    data: {
      checkedIn: 3,
      total: 10,
      byGate: [{ gateId: 'g1', name: 'Gate 1', checkedIn: 2 }, { gateId: 'g2', name: 'Gate 2', checkedIn: 1 }],
      byScanner: [{ name: 'Ravi', checkedIn: 3 }],
      lastScanAt: '2026-11-07T14:00:00Z',
    },
    isPending: false,
  })
  render(
    <MemoryRouter>
      <CheckInTab eventId="e1" />
    </MemoryRouter>,
  )

  expect(screen.getByText('3 of 10 checked in')).toBeInTheDocument()
  expect(screen.getByText('Gate 1 · 2')).toBeInTheDocument()
  expect(screen.getByText('Ravi · 3')).toBeInTheDocument()
  expect(checkin.useCheckInStats).toHaveBeenCalledWith('e1', { poll: true })
  expect(screen.getByRole('link', { name: /open scanner/i })).toHaveAttribute('href', '/scan/e1')
  expect(screen.getByRole('cell', { name: 'Not a ticket / invalid' })).toBeInTheDocument() // scan log row label for INVALID
})
