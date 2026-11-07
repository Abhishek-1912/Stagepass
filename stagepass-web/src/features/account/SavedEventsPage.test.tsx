import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useSavedEvents: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('@/features/engagement/SaveButton', () => ({ SaveButton: () => null }))

import { SavedEventsPage } from './SavedEventsPage'

const card = {
  id: 'e1', slug: 'sunburn', title: 'Sunburn Goa', summary: null, coverImageUrl: null, startsAt: '2026-12-20T13:30:00Z',
  endsAt: '2026-12-20T18:30:00Z', online: false, venueName: 'Vagator', city: 'Goa', categorySlug: 'music', categoryName: 'Music',
  minPricePaise: 49900, soldOut: false, featured: false, distanceKm: null,
}

beforeEach(() => {
  api.useSavedEvents.mockReturnValue({ data: { items: [], totalItems: 0, totalPages: 0, page: 0 }, isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <SavedEventsPage />
    </MemoryRouter>,
  )
}

test('saved page empty state', () => {
  renderPage()
  expect(screen.getByText(/nothing saved yet/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /explore events/i })).toHaveAttribute('href', '/explore')
})

test('saved events show, with cancelled ones marked', () => {
  api.useSavedEvents.mockReturnValue({
    data: { items: [{ event: card, status: 'PUBLISHED', savedAt: '2026-11-01T10:00:00Z' },
      { event: { ...card, id: 'e2', slug: 'gone', title: 'Gone Gig' }, status: 'CANCELLED', savedAt: '2026-11-01T09:00:00Z' }],
    totalItems: 2, totalPages: 1, page: 0 },
    isPending: false,
  })
  renderPage()
  expect(screen.getByText('Sunburn Goa')).toBeInTheDocument()
  expect(screen.getByText('Gone Gig')).toBeInTheDocument()
  expect(screen.getByText('Cancelled')).toBeInTheDocument()
})
