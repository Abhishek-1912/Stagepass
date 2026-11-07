import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({
  useUnreadCount: vi.fn(),
  useNotifications: vi.fn(),
  useMarkRead: vi.fn(),
  useMarkAllRead: vi.fn(),
}))
vi.mock('@/features/notifications/api', () => hooks)

import { NotificationBell } from './NotificationBell'

const markRead = vi.fn()
const markAll = vi.fn()

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderBell() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="*" element={<><NotificationBell /><Where /></>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  markRead.mockReset().mockResolvedValue(undefined)
  markAll.mockReset().mockResolvedValue(undefined)
  hooks.useUnreadCount.mockReturnValue({ data: { count: 12 } })
  hooks.useNotifications.mockReturnValue({
    data: {
      items: [
        { id: 'n1', type: 'BOOKING_CONFIRMED', title: 'Booking confirmed: Sunburn Goa', body: '2 tickets ready', link: '/account/bookings/b1', createdAt: new Date().toISOString(), read: false },
        { id: 'n2', type: 'EVENT_REMINDER', title: 'Tomorrow: Comedy Night', body: null, link: '/account/bookings/b2', createdAt: new Date().toISOString(), read: true },
      ],
    },
    isPending: false,
  })
  hooks.useMarkRead.mockReturnValue({ mutate: markRead })
  hooks.useMarkAllRead.mockReturnValue({ mutate: markAll, isPending: false })
})

test('bell shows 9+ and marks an item read when opened', async () => {
  renderBell()
  const bell = screen.getByRole('button', { name: /notifications, 12 unread/i })
  expect(bell).toHaveTextContent('9+')
  await userEvent.click(bell)
  await userEvent.click(await screen.findByText('Booking confirmed: Sunburn Goa'))
  expect(markRead).toHaveBeenCalledWith('n1')
  expect(screen.getByTestId('where')).toHaveTextContent('/account/bookings/b1')
})

test('mark all as read and empty state', async () => {
  renderBell()
  await userEvent.click(screen.getByRole('button', { name: /notifications/i }))
  await userEvent.click(await screen.findByText(/mark all as read/i))
  expect(markAll).toHaveBeenCalled()
})

test('empty list says all caught up', async () => {
  hooks.useUnreadCount.mockReturnValue({ data: { count: 0 } })
  hooks.useNotifications.mockReturnValue({ data: { items: [] }, isPending: false })
  renderBell()
  const bell = screen.getByRole('button', { name: 'Notifications' })
  await userEvent.click(bell)
  expect(await screen.findByText(/you're all caught up/i)).toBeInTheDocument()
})
