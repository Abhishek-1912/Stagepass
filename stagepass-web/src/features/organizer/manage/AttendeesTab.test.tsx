import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'

const manage = vi.hoisted(() => ({ useEventAttendees: vi.fn() }))
vi.mock('./api', () => manage)
const downloads = vi.hoisted(() => ({ downloadFile: vi.fn(async () => {}) }))
vi.mock('@/features/bookings/downloads', () => downloads)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { AttendeesTab } from './AttendeesTab'

const row = {
  ticketId: 't1',
  ticketCode: 'SP-AAAA-2222',
  ticketType: 'General',
  holderName: 'Asha Rao',
  bookingCode: 'SP-ABC234',
  attendeeEmail: 'asha@test.in',
  attendeePhone: '9876543210',
  status: 'VALID',
  bookedAt: '2026-11-05T10:00:00Z',
  checkedInAt: null,
}

beforeEach(() => {
  manage.useEventAttendees.mockReset().mockReturnValue({
    data: { items: [row], page: 0, size: 20, totalItems: 41, totalPages: 3 },
    isPending: false,
  })
  downloads.downloadFile.mockClear()
})
afterEach(() => vi.useRealTimers())

test('attendee export downloads the CSV', async () => {
  render(<AttendeesTab eventId="e1" />)
  expect(screen.getByText('SP-AAAA-2222')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /export csv/i }))
  expect(downloads.downloadFile).toHaveBeenCalledWith('/organizer/events/e1/attendees.csv', 'attendees.csv')
})

test('search is debounced and resets to page 0', async () => {
  vi.useFakeTimers()
  render(<AttendeesTab eventId="e1" />)
  fireEvent.click(screen.getByRole('button', { name: /next page/i }))
  expect(manage.useEventAttendees).toHaveBeenLastCalledWith('e1', { q: '', status: '', page: 1 })

  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'asha' } })
  expect(manage.useEventAttendees).toHaveBeenLastCalledWith('e1', { q: '', status: '', page: 1 })
  await act(async () => {
    vi.advanceTimersByTime(300)
  })
  expect(manage.useEventAttendees).toHaveBeenLastCalledWith('e1', { q: 'asha', status: '', page: 0 })
})
