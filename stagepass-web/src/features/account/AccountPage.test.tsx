import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const me = vi.hoisted(() => ({ useMe: vi.fn(), useUpdateMe: vi.fn() }))
vi.mock('@/features/auth/useMe', () => me)
const bookings = vi.hoisted(() => ({ useMyBookings: vi.fn() }))
vi.mock('@/features/bookings/api', () => bookings)
vi.mock('./AvatarUpload', () => ({ AvatarUpload: () => <div>avatar</div> }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { AccountPage } from './AccountPage'

const profile = {
  id: 'u1',
  email: 'asha@stagepass.test',
  fullName: 'Asha Rao',
  phone: null,
  avatarUrl: null,
  city: 'Pune',
  roles: ['ATTENDEE'],
  emailReminders: true,
}
const update = vi.fn()

function booking(i: number) {
  return { id: `b${i}`, code: `SP-${i}`, status: 'CONFIRMED', eventTitle: `Gig ${i}`, eventSlug: `gig-${i}`, coverImageUrl: null,
    eventStartsAt: '2026-12-20T13:30:00Z', city: 'Pune', ticketCount: 1, totalPaise: 49900 }
}

beforeEach(() => {
  update.mockReset().mockResolvedValue(profile)
  me.useMe.mockReturnValue({ data: profile, isPending: false, error: null })
  me.useUpdateMe.mockReturnValue({ mutateAsync: update, isPending: false })
  bookings.useMyBookings.mockReturnValue({ data: { items: [1, 2, 3, 4].map(booking), totalItems: 4 }, isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AccountPage />
    </MemoryRouter>,
  )
}

test('profile form saves changed fields and shows field errors', async () => {
  update.mockRejectedValueOnce(new ApiError(400, 'VALIDATION_FAILED', 'Request validation failed', [
    { field: 'phone', message: 'must be a 10-digit Indian mobile number' },
  ]))
  renderPage()
  await userEvent.type(screen.getByLabelText('Phone'), '12345')
  await userEvent.click(screen.getByRole('button', { name: /save profile/i }))
  expect(update).toHaveBeenCalledWith({ phone: '12345' })
  expect(await screen.findByText('must be a 10-digit Indian mobile number')).toBeInTheDocument()
})

test('reminder switch saves emailReminders', async () => {
  renderPage()
  await userEvent.click(screen.getByRole('switch', { name: /email me a reminder/i }))
  expect(update).toHaveBeenCalledWith({ emailReminders: false })
})

test('upcoming tickets show the next three bookings', () => {
  renderPage()
  expect(screen.getByText('Gig 1')).toBeInTheDocument()
  expect(screen.getByText('Gig 3')).toBeInTheDocument()
  expect(screen.queryByText('Gig 4')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: /all tickets/i })).toHaveAttribute('href', '/account/bookings')
  expect(screen.getByRole('link', { name: /saved events/i })).toHaveAttribute('href', '/account/saved')
})
