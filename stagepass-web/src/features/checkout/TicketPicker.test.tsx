import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { publicEvent, publicType } from '@/features/event/testDetail'

const api = vi.hoisted(() => ({ useQuote: vi.fn() }))
vi.mock('./api', () => api)

import { TicketPicker } from './TicketPicker'

const price = { subtotalPaise: 99800, feePaise: 4495, feeGstPaise: 809, totalPaise: 105104, commissionPaise: 0, ticketCount: 2 }

beforeEach(() => {
  api.useQuote.mockReset().mockImplementation((_id: string | undefined, items: unknown[]) =>
    items.length ? { data: { lines: [], price, holdMinutes: 10 }, error: null } : { data: undefined, error: null },
  )
})

function Where() {
  const l = useLocation()
  return <p data-testid="where">{l.pathname + l.search}</p>
}

function renderPicker(event = publicEvent()) {
  return render(
    <MemoryRouter initialEntries={['/events/x']}>
      <Routes>
        <Route path="/events/x" element={<TicketPicker event={event} />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('picker respects min/max per order and the per-person limit', async () => {
  renderPicker(
    publicEvent({
      maxTicketsPerUser: 5,
      ticketTypes: [
        publicType({ id: 'aaaaaaaa-0000-0000-0000-000000000001', name: 'Pairs', minPerOrder: 2, maxPerOrder: 4 }),
        publicType({ id: 'aaaaaaaa-0000-0000-0000-000000000002', name: 'Solo', minPerOrder: 1, maxPerOrder: 10 }),
      ],
    }),
  )
  const addPairs = screen.getByRole('button', { name: 'Add Pairs' })
  const addSolo = screen.getByRole('button', { name: 'Add Solo' })

  await userEvent.click(addPairs)
  expect(screen.getByRole('group', { name: 'Pairs quantity' })).toHaveTextContent('2')
  await userEvent.click(addPairs)
  await userEvent.click(addPairs)
  expect(screen.getByRole('group', { name: 'Pairs quantity' })).toHaveTextContent('4')
  expect(addPairs).toBeDisabled()

  await userEvent.click(addSolo)
  expect(addSolo).toBeDisabled() // 4 + 1 = per-person limit of 5

  await userEvent.click(screen.getByRole('button', { name: 'Remove Pairs' }))
  await userEvent.click(screen.getByRole('button', { name: 'Remove Pairs' }))
  expect(screen.getByRole('group', { name: 'Pairs quantity' })).toHaveTextContent('2')
  await userEvent.click(screen.getByRole('button', { name: 'Remove Pairs' }))
  expect(screen.getByRole('group', { name: 'Pairs quantity' })).toHaveTextContent('0') // below min drops to 0
})

test('picker hides sold-out and not-started types from steppers', () => {
  renderPicker(
    publicEvent({
      ticketTypes: [
        publicType({ id: 'a', name: 'Early bird', availability: 'SOLD_OUT' }),
        publicType({ id: 'b', name: 'Late', availability: 'NOT_STARTED' }),
        publicType({ id: 'c', name: 'General' }),
      ],
    }),
  )
  expect(screen.queryByRole('button', { name: 'Add Early bird' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Add Late' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Add General' })).toBeEnabled()
})

test('continue shows the server total and goes to checkout', async () => {
  renderPicker(publicEvent({ ticketTypes: [publicType({ id: 'aaaaaaaa-0000-0000-0000-000000000003', name: 'General' })] }))
  expect(screen.getByRole('button', { name: 'Select tickets' })).toBeDisabled()

  await userEvent.click(screen.getByRole('button', { name: 'Add General' }))
  await userEvent.click(screen.getByRole('button', { name: 'Add General' }))
  const go = await screen.findByRole('button', { name: /continue · ₹1,051\.04/i })

  await userEvent.click(go)
  expect(screen.getByTestId('where')).toHaveTextContent(
    '/checkout/sunburn-goa-abc123?items=aaaaaaaa-0000-0000-0000-000000000003:2',
  )
})

test('cancelled and not-yet-on-sale events cannot be booked', () => {
  const { unmount } = renderPicker(publicEvent({ status: 'CANCELLED' }))
  expect(screen.getByRole('button', { name: /event cancelled/i })).toBeDisabled()
  unmount()
  renderPicker(publicEvent({ ticketTypes: [publicType({ availability: 'NOT_STARTED' })] }))
  expect(screen.getByRole('button', { name: /tickets on sale soon/i })).toBeDisabled()
})
