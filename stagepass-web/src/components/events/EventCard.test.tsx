import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, test } from 'vitest'
import type { PublicEventCard } from '@/features/discovery/types'
import { card } from './testCards'
import { EventCard } from './EventCard'


const renderCard = (c: PublicEventCard) =>
  render(
    <MemoryRouter>
      <EventCard event={c} />
    </MemoryRouter>,
  )

test('shows from-price, free and sold-out states', () => {
  const { unmount } = renderCard(card())
  expect(screen.getByText('From ₹1,499')).toBeInTheDocument()
  unmount()

  const free = renderCard(card({ minPricePaise: 0 }))
  expect(screen.getByText('Free')).toBeInTheDocument()
  free.unmount()

  renderCard(card({ soldOut: true }))
  expect(screen.getByText('Sold out')).toBeInTheDocument()
})

test('links to the event page and shows the IST date', () => {
  renderCard(card())
  expect(screen.getByRole('link')).toHaveAttribute('href', '/events/sunburn-goa-abc123')
  expect(screen.getByText('20')).toBeInTheDocument()
  expect(screen.getByText(/dec/i)).toBeInTheDocument()
  expect(screen.getByText(/Vagator Beach · Goa/)).toBeInTheDocument()
})

test('requests an optimised cover and labels online events', () => {
  const { container } = renderCard(card({ online: true, venueName: null, city: null }))
  expect(container.querySelector('img')).toHaveAttribute(
    'src',
    'https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_640/stagepass/events/e1/cover.jpg',
  )
  expect(screen.getByText('Online')).toBeInTheDocument()
})
