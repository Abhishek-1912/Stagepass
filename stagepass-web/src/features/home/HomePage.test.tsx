import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { card } from '@/components/events/testCards'

const api = vi.hoisted(() => ({ usePublicHome: vi.fn(), useCities: vi.fn() }))
vi.mock('@/features/discovery/api', () => api)

import { HomePage } from './HomePage'

const empty = { featured: [], trending: [], thisWeekend: [], upcoming: [], nearby: [], categories: [] }

beforeEach(() => {
  api.useCities.mockReturnValue({ data: [{ name: 'Goa', upcomingEvents: 3 }] })
})

const renderHome = () =>
  render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  )

test('shows featured events and hides empty feeds', () => {
  api.usePublicHome.mockReturnValue({ data: { ...empty, featured: [card({ title: 'Sunburn Goa', featured: true })] }, isPending: false })
  renderHome()

  expect(screen.getByRole('heading', { name: /featured/i })).toBeInTheDocument()
  expect(screen.getByText('Sunburn Goa')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: /trending/i })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: /this weekend/i })).not.toBeInTheDocument()
})

test('renders category rails', () => {
  api.usePublicHome.mockReturnValue({
    data: { ...empty, categories: [{ slug: 'comedy', name: 'Comedy', icon: 'laugh', events: [card({ id: 'c1', title: 'Standup Night' })] }] },
    isPending: false,
  })
  renderHome()
  expect(screen.getByRole('heading', { name: 'Comedy' })).toBeInTheDocument()
  expect(screen.getByText('Standup Night')).toBeInTheDocument()
})

test('offers a retry when events fail to load', () => {
  const refetch = vi.fn()
  api.usePublicHome.mockReturnValue({ data: undefined, isPending: false, isError: true, refetch })
  renderHome()
  expect(screen.getByText(/couldn’t load events/i)).toBeInTheDocument()
  screen.getByRole('button', { name: /try again/i }).click()
  expect(refetch).toHaveBeenCalled()
})

test('shows an inviting empty state when nothing is live yet', () => {
  api.usePublicHome.mockReturnValue({ data: empty, isPending: false })
  renderHome()
  expect(screen.getByText(/new events are on the way/i)).toBeInTheDocument()
})
