import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { publicEvent, publicType } from './testDetail'
import { TicketList } from './TicketList'

test('shows availability states and never exact counts', () => {
  render(
    <TicketList
      event={publicEvent({
        ticketTypes: [
          publicType({ id: 'a', name: 'Early bird', availability: 'SOLD_OUT' }),
          publicType({ id: 'b', name: 'General', fewLeft: true }),
          publicType({ id: 'c', name: 'VIP', availability: 'NOT_STARTED', saleStartsAt: '2026-12-01T04:30:00Z', pricePaise: 149900 }),
          publicType({ id: 'd', name: 'Door', availability: 'ENDED' }),
        ],
      })}
    />,
  )

  expect(screen.getByText('Sold out')).toBeInTheDocument()
  expect(screen.getByText('Few left')).toBeInTheDocument()
  expect(screen.getByText(/sales open 1 dec/i)).toBeInTheDocument()
  expect(screen.getByText('Sales ended')).toBeInTheDocument()
  expect(screen.getByText('₹1,499')).toBeInTheDocument()
  expect(screen.queryByText(/\d+ left/)).not.toBeInTheDocument()
})

test('cancelled events show no on-sale signals', () => {
  render(<TicketList event={publicEvent({ status: 'CANCELLED', ticketTypes: [publicType({ fewLeft: true })] })} />)
  expect(screen.queryByText('Few left')).not.toBeInTheDocument()
})
