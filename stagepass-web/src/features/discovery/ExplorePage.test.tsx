import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { card } from '@/components/events/testCards'

const discovery = vi.hoisted(() => ({ usePublicEvents: vi.fn(), useCities: vi.fn(), PAGE_SIZE: 24 }))
vi.mock('./api', () => discovery)
vi.mock('@/features/organizer/api', () => ({
  useCategories: () => ({ data: [{ id: 'c1', name: 'Comedy', slug: 'comedy', icon: 'laugh', sortOrder: 7, active: true }] }),
}))

import { ExplorePage } from './ExplorePage'

function results(items = [card()], totalItems = items.length) {
  return {
    data: { pages: [{ items, page: 0, size: 24, totalItems, totalPages: 1 }] },
    isPending: false,
    hasNextPage: false,
    fetchNextPage: vi.fn(),
    isFetchingNextPage: false,
  }
}

function Location() {
  const l = useLocation()
  return <output data-testid="location">{l.search}</output>
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ExplorePage />
      <Location />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  discovery.usePublicEvents.mockReset().mockReturnValue(results())
  discovery.useCities.mockReturnValue({ data: [] })
})

test('reads filters from the URL', () => {
  renderAt('/explore?city=Goa&category=comedy')
  expect(discovery.usePublicEvents).toHaveBeenLastCalledWith({ city: 'Goa', category: 'comedy' })
  expect(screen.getByRole('heading', { name: '1 event in Goa' })).toBeInTheDocument()
})

test('choosing a date chip updates the URL and query', async () => {
  renderAt('/explore')
  await userEvent.click(screen.getByRole('button', { name: 'This weekend' }))

  expect(screen.getByTestId('location')).toHaveTextContent('?date=weekend')
  expect(discovery.usePublicEvents).toHaveBeenLastCalledWith({ date: 'weekend' })
})

test('a chip clicked while typing is kept when the search applies', async () => {
  renderAt('/explore')
  await userEvent.type(screen.getByRole('searchbox'), 'gig')
  await userEvent.click(screen.getByRole('button', { name: 'This weekend' }))
  await new Promise((r) => setTimeout(r, 400))

  const search = new URLSearchParams(screen.getByTestId('location').textContent ?? '')
  expect(search.get('q')).toBe('gig')
  expect(search.get('date')).toBe('weekend')
})

test('clicking the active chip clears it', async () => {
  renderAt('/explore?category=comedy')
  await userEvent.click(screen.getByRole('button', { name: 'Comedy' }))
  expect(screen.getByTestId('location')).toHaveTextContent(/^$/)
})

test('empty results offer clear filters', async () => {
  discovery.usePublicEvents.mockReturnValue(results([], 0))
  renderAt('/explore?q=nothing&free=true')

  expect(screen.getByText(/no events match/i)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /clear filters/i }))
  expect(screen.getByTestId('location')).toHaveTextContent(/^$/)
})
