import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useLedger: vi.fn() }))
vi.mock('./api', () => api)

import { PayoutsPage } from './PayoutsPage'

function ledger(over: { payablePaise?: number; negative?: boolean } = {}) {
  return {
    balances: {
      pendingPaise: 49900,
      payablePaise: over.payablePaise ?? 99800,
      paidOutPaise: 10000,
      balancePaise: 149700,
      negative: over.negative ?? false,
      events: [
        { eventId: 'e1', title: 'Sunburn Goa', endsAt: '2026-11-01T18:30:00Z', earnedPaise: 99800, payableFrom: '2026-11-04T18:30:00Z', payable: true },
        { eventId: 'e2', title: 'Comedy Night', endsAt: '2026-12-01T18:30:00Z', earnedPaise: 49900, payableFrom: '2026-12-04T18:30:00Z', payable: false },
      ],
    },
    entries: {
      items: [{ id: 'l1', type: 'SALE', amountPaise: 99800, eventId: 'e1', eventTitle: 'Sunburn Goa', bookingCode: 'SP-ABC234', note: null, createdAt: '2026-11-01T10:00:00Z' }],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    },
    payouts: [{ id: 'p1', amountPaise: 10000, reference: 'UTR123', paidAt: '2026-11-05T10:00:00Z', note: null }],
  }
}

beforeEach(() => api.useLedger.mockReturnValue({ data: ledger(), isPending: false, error: null }))

function renderPage() {
  return render(
    <MemoryRouter>
      <PayoutsPage />
    </MemoryRouter>,
  )
}

test('shows balances and the negative-balance banner', () => {
  renderPage()
  expect(within(screen.getByText('Payable now').closest('div')!.parentElement!).getByText('₹998')).toBeInTheDocument()
  expect(within(screen.getAllByText('Pending')[0].closest('div')!.parentElement!).getByText('₹499')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()

  api.useLedger.mockReturnValue({ data: ledger({ payablePaise: -49900, negative: true }), isPending: false, error: null })
  renderPage()
  expect(screen.getByRole('alert')).toHaveTextContent('You owe Stagepass ₹499')
})

test('lists per-event payable dates and ledger entries', () => {
  renderPage()
  const rows = screen.getAllByRole('row')
  expect(rows.some((r) => r.textContent?.includes('Comedy Night') && r.textContent.includes('Pending'))).toBe(true)
  expect(rows.some((r) => r.textContent?.includes('Sunburn Goa') && r.textContent.includes('Payable'))).toBe(true)
  expect(screen.getByText('SP-ABC234')).toBeInTheDocument()
  expect(screen.getByText('UTR123')).toBeInTheDocument()
  expect(screen.getByText(/payable 3 days after each event ends/i)).toBeInTheDocument()
})
