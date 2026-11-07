import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'
import { publicEvent } from './testDetail'

const discovery = vi.hoisted(() => ({ usePublicEvent: vi.fn() }))
vi.mock('@/features/discovery/api', () => discovery)
vi.mock('./ReportEventLink', () => ({ ReportEventLink: () => null }))
vi.mock('@/features/engagement/ReviewsSection', () => ({ ReviewsSection: () => <section>reviews</section> }))
vi.mock('@/features/engagement/SaveButton', () => ({ SaveButton: () => null }))
vi.mock('@/features/checkout/api', () => ({ useQuote: () => ({ data: undefined, error: null }) }))
const maps = vi.hoisted(() => ({ mapsKey: vi.fn(() => undefined as string | undefined) }))
vi.mock('@/lib/maps', async (orig) => ({ ...(await orig<typeof import('@/lib/maps')>()), mapsKey: maps.mapsKey }))

import { EventPage } from './EventPage'

function renderAt(slug = 'sunburn-goa-abc123') {
  return render(
    <MemoryRouter initialEntries={[`/events/${slug}`]}>
      <Routes>
        <Route path="/events/:slug" element={<EventPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => maps.mapsKey.mockReturnValue(undefined))

test('renders the event with organizer, terms and refund policy', () => {
  discovery.usePublicEvent.mockReturnValue({ data: publicEvent(), isPending: false })
  renderAt()

  expect(screen.getByRole('heading', { level: 1, name: 'Sunburn Goa' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /acme live/i })).toHaveAttribute('href', '/o/acme-live-x1')
  expect(screen.getByText('No re-entry')).toBeInTheDocument()
  expect(screen.getByText(/refunds are available until 48 hours before/i)).toBeInTheDocument()
  expect(document.title).toBe('Sunburn Goa · Stagepass')
})

test('cancelled event shows banner and no booking', () => {
  discovery.usePublicEvent.mockReturnValue({ data: publicEvent({ status: 'CANCELLED' }), isPending: false })
  renderAt()

  expect(screen.getByRole('alert')).toHaveTextContent(/this event has been cancelled/i)
  expect(screen.getAllByRole('button', { name: /event cancelled/i })[0]).toBeDisabled()
  expect(screen.queryByRole('button', { name: /add to calendar/i })).not.toBeInTheDocument()
})

test('map falls back to a Google Maps link without a key', () => {
  discovery.usePublicEvent.mockReturnValue({ data: publicEvent(), isPending: false })
  renderAt()

  expect(screen.queryByTitle(/map of vagator beach/i)).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: /open in google maps/i })).toHaveAttribute(
    'href',
    'https://www.google.com/maps/search/?api=1&query=15.6%2C73.73',
  )
})

test('embeds a map when a key is configured', () => {
  maps.mapsKey.mockReturnValue('browser-key')
  discovery.usePublicEvent.mockReturnValue({ data: publicEvent(), isPending: false })
  renderAt()

  expect(screen.getByTitle(/map of vagator beach/i)).toHaveAttribute(
    'src',
    'https://www.google.com/maps/embed/v1/place?key=browser-key&q=15.6%2C73.73',
  )
})

test('unknown event shows not found', () => {
  discovery.usePublicEvent.mockReturnValue({ data: undefined, isPending: false, error: new ApiError(404, 'NOT_FOUND', 'Event not found') })
  renderAt('nope')
  expect(screen.getByText(/this event isn’t available/i)).toBeInTheDocument()
})
