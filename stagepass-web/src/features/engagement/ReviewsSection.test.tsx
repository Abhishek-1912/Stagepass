import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({
  useEventReviews: vi.fn(),
  useMyReview: vi.fn(),
  useSaveReview: vi.fn(),
  useDeleteReview: vi.fn(),
  useAdminRemoveReview: vi.fn(),
}))
vi.mock('./reviewsApi', () => hooks)
const viewer = vi.hoisted(() => ({ signedIn: true, roles: ['ATTENDEE'] as string[] }))
vi.mock('./useViewer', () => ({ useViewer: () => viewer }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { ReviewsSection } from './ReviewsSection'

function review(i: number) {
  return { id: `r${i}`, rating: 4, comment: `Comment ${i}`, authorName: `Person${i}`, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z' }
}

const fetchNextPage = vi.fn()
const remove = vi.fn()
const event = { id: 'e1', slug: 'sunburn', endsAt: '2026-11-01T18:00:00Z', status: 'COMPLETED', rating: { average: 4.0, count: 12 } }

beforeEach(() => {
  viewer.signedIn = true
  viewer.roles = ['ATTENDEE']
  fetchNextPage.mockReset()
  remove.mockReset().mockResolvedValue(undefined)
  hooks.useEventReviews.mockReturnValue({
    data: { pages: [{ items: [1, 2, 3, 4, 5].map(review), totalItems: 12, totalPages: 3, page: 0 }] },
    hasNextPage: true,
    fetchNextPage,
    isFetchingNextPage: false,
    isPending: false,
  })
  hooks.useMyReview.mockReturnValue({ data: { eligible: false, review: null } })
  hooks.useSaveReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useDeleteReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useAdminRemoveReview.mockReturnValue({ mutateAsync: remove, isPending: false })
})

test('summary and newest reviews show, with show more', async () => {
  render(<ReviewsSection event={event} />)
  expect(screen.getByText('4.0')).toBeInTheDocument()
  expect(screen.getByText('12 reviews')).toBeInTheDocument()
  expect(screen.getAllByRole('img', { name: '4 out of 5 stars' }).length).toBeGreaterThan(0)
  expect(screen.getAllByRole('article')).toHaveLength(5)
  await userEvent.click(screen.getByRole('button', { name: /show more/i }))
  expect(fetchNextPage).toHaveBeenCalled()
})

test('eligible user can rate; ineligible sees no button', () => {
  const { rerender } = render(<ReviewsSection event={event} />)
  expect(screen.queryByRole('button', { name: /rate this event/i })).not.toBeInTheDocument()
  hooks.useMyReview.mockReturnValue({ data: { eligible: true, review: null } })
  rerender(<ReviewsSection event={event} />)
  expect(screen.getByRole('button', { name: /rate this event/i })).toBeInTheDocument()
  hooks.useMyReview.mockReturnValue({ data: { eligible: true, review: review(9) } })
  rerender(<ReviewsSection event={event} />)
  expect(screen.getByRole('button', { name: /edit your review/i })).toBeInTheDocument()
})

test('admin sees Remove and it asks to confirm', async () => {
  viewer.roles = ['ATTENDEE', 'ADMIN']
  render(<ReviewsSection event={event} />)
  await userEvent.click(screen.getAllByRole('button', { name: /remove/i })[0])
  await userEvent.click(screen.getByRole('button', { name: 'Remove review' }))
  expect(remove).toHaveBeenCalledWith('r1')
})

test('no reviews yet on a finished event', () => {
  hooks.useEventReviews.mockReturnValue({ data: { pages: [{ items: [], totalItems: 0, totalPages: 0, page: 0 }] }, hasNextPage: false, fetchNextPage, isPending: false })
  render(<ReviewsSection event={{ ...event, rating: { average: null, count: 0 } }} />)
  expect(screen.getByText(/no reviews yet/i)).toBeInTheDocument()
})
