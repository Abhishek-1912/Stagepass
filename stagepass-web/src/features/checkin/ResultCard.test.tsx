import { render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { ResultCard } from './ResultCard'
import type { ScanResponse } from './types'

const counts = { checkedIn: 5, total: 20 }
const ticket = { code: 'SP-AAAA-2222', holderName: 'Asha Rao', ticketType: 'General' }

function show(result: ScanResponse) {
  return render(<ResultCard result={result} onDismiss={vi.fn()} />)
}

test('valid welcomes the holder', () => {
  show({ result: 'VALID', message: 'Welcome in', ticket, counts })
  expect(screen.getByRole('status')).toHaveTextContent('Welcome in')
  expect(screen.getByText('Asha Rao')).toBeInTheDocument()
  expect(screen.getByText('General')).toBeInTheDocument()
})

test('already used by me reads "Checked in by you just now"', () => {
  show({ result: 'ALREADY_USED', message: 'Already used', ticket, counts, firstScan: { at: new Date().toISOString(), gateName: 'Gate 1', scannerName: 'Me', byYou: true } })
  expect(screen.getByText('Already used')).toBeInTheDocument()
  expect(screen.getByText(/checked in by you just now/i)).toBeInTheDocument()
})

test('already used by someone else says where and who', () => {
  show({ result: 'ALREADY_USED', message: 'Already used', ticket, counts, firstScan: { at: '2026-11-07T13:00:00Z', gateName: 'Gate 1', scannerName: 'Ravi', byYou: false } })
  expect(screen.getByText(/at Gate 1 by Ravi/)).toBeInTheDocument()
})

test('wrong gate names the allowed gates', () => {
  show({ result: 'WRONG_GATE', message: 'Wrong gate. Use Main Gate', ticket, counts, allowedGates: ['Main Gate', 'Side Gate'] })
  expect(screen.getByText('Wrong gate')).toBeInTheDocument()
  expect(screen.getByText('Use Main Gate or Side Gate')).toBeInTheDocument()
})

test('invalid shows the reason', () => {
  show({ result: 'INVALID', message: 'This ticket was refunded', counts })
  expect(screen.getByText('Not valid')).toBeInTheDocument()
  expect(screen.getByText('This ticket was refunded')).toBeInTheDocument()
})
