import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { SalesChart } from './SalesChart'

const points = [
  { date: '2026-11-12', tickets: 2, amountPaise: 99800 },
  { date: '2026-11-13', tickets: 0, amountPaise: 0 },
  { date: '2026-11-14', tickets: 1, amountPaise: 5304 },
]

test('chart labels each day and the range total', () => {
  render(<SalesChart points={points} metric="amount" />)

  expect(screen.getByRole('img', { name: /₹1,051\.04 over 3 days/ })).toBeInTheDocument()
  expect(document.querySelectorAll('[data-bar]')).toHaveLength(2) // no bar on the day without sales
  expect(document.querySelectorAll('[data-hit]')).toHaveLength(3)
  expect(screen.getByText('12 Nov: 2 tickets · ₹998.00')).toBeInTheDocument() // <title> of the first bar
  expect(screen.getByRole('table', { name: /sales by day/i })).toBeInTheDocument()
})

test('hovering a bar shows its tooltip', () => {
  render(<SalesChart points={points} metric="tickets" />)
  fireEvent.mouseEnter(document.querySelectorAll('[data-hit]')[2])
  expect(screen.getByRole('tooltip')).toHaveTextContent('14 Nov')
  expect(screen.getByRole('tooltip')).toHaveTextContent('1 ticket')
})

test('chart shows an empty state', () => {
  render(<SalesChart points={points.map((p) => ({ ...p, tickets: 0, amountPaise: 0 }))} metric="amount" />)
  expect(screen.getByText('No sales in this period')).toBeInTheDocument()
})
