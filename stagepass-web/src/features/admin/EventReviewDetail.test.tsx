import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { eventDetail, ticketType } from '@/features/organizer/events/testEvent'

const hooks = vi.hoisted(() => ({
  useAdminEvent: vi.fn(),
  useApproveEvent: vi.fn(),
  useRejectEvent: vi.fn(),
  useFeatureEvent: vi.fn(),
}))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { EventReviewDetail } from './EventReviewDetail'

const approve = vi.fn()
const feature = vi.fn()
beforeEach(() => {
  approve.mockReset().mockResolvedValue(eventDetail({ status: 'PUBLISHED' }))
  feature.mockReset().mockResolvedValue(eventDetail({ status: 'PUBLISHED', featured: true }))
  hooks.useApproveEvent.mockReturnValue({ mutateAsync: approve, isPending: false })
  hooks.useRejectEvent.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useFeatureEvent.mockReturnValue({ mutateAsync: feature, isPending: false })
})

function renderAt(event = eventDetail({ status: 'PENDING_REVIEW' })) {
  hooks.useAdminEvent.mockReturnValue({ data: event, isPending: false })
  return render(
    <MemoryRouter initialEntries={['/admin/events/e1']}>
      <Routes>
        <Route path="/admin/events/:id" element={<EventReviewDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('shows ticket prices and approves', async () => {
  renderAt(
    eventDetail({
      status: 'PENDING_REVIEW',
      ticketTypes: [ticketType(), ticketType({ id: 't2', name: 'VIP', pricePaise: 149900, quantity: 50 })],
    }),
  )

  expect(screen.getByText('₹499')).toBeInTheDocument()
  expect(screen.getByText('₹1,499')).toBeInTheDocument()
  expect(screen.getByText('Acme Live')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: /approve and publish/i }))
  expect(approve).toHaveBeenCalledWith('e1')
})

test('published event can be featured but not approved again', async () => {
  renderAt(eventDetail({ status: 'PUBLISHED' }))

  expect(screen.queryByRole('button', { name: /approve and publish/i })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /feature on homepage/i }))
  expect(feature).toHaveBeenCalledWith({ id: 'e1', featured: true })
})
