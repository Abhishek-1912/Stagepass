import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useStaff: vi.fn(), useInviteStaff: vi.fn(), useRemoveStaff: vi.fn() }))
vi.mock('./api', () => api)
const organizer = vi.hoisted(() => ({ useOrganizerEvents: vi.fn() }))
vi.mock('../api', () => organizer)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { StaffPage } from './StaffPage'

const invite = vi.fn()
const remove = vi.fn()
beforeEach(() => {
  invite.mockReset().mockResolvedValue({})
  remove.mockReset().mockResolvedValue(undefined)
  api.useStaff.mockReturnValue({
    data: [
      { id: 's1', email: 'ravi@test.in', name: 'Ravi', eventTitle: 'Sunburn Goa', eventId: 'e1', status: 'ACTIVE', createdAt: '2026-11-05T10:00:00Z', acceptedAt: '2026-11-05T12:00:00Z' },
      { id: 's2', email: 'meena@test.in', status: 'ACTIVE', createdAt: '2026-11-06T10:00:00Z' },
    ],
    isPending: false,
  })
  api.useInviteStaff.mockReturnValue({ mutateAsync: invite, isPending: false })
  api.useRemoveStaff.mockReturnValue({ mutateAsync: remove, isPending: false })
  organizer.useOrganizerEvents.mockReturnValue({
    data: { items: [{ id: 'e1', title: 'Sunburn Goa', status: 'PUBLISHED', startsAt: '2026-12-20T13:30:00Z' }] },
  })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <StaffPage />
    </MemoryRouter>,
  )
}

test('invites door staff for one event or all events', async () => {
  renderPage()
  await userEvent.type(screen.getByLabelText(/email/i), 'door@test.in')
  await userEvent.click(screen.getByRole('button', { name: /send invite/i }))
  expect(invite).toHaveBeenLastCalledWith({ email: 'door@test.in', eventId: null })

  await userEvent.type(screen.getByLabelText(/email/i), 'gate@test.in')
  await userEvent.selectOptions(screen.getByLabelText(/events/i), 'e1')
  await userEvent.click(screen.getByRole('button', { name: /send invite/i }))
  expect(invite).toHaveBeenLastCalledWith({ email: 'gate@test.in', eventId: 'e1' })
})

test('shows who has signed in and what they can scan', () => {
  renderPage()
  const ravi = screen.getByText('Ravi').closest('li')!
  expect(within(ravi).getByText('Active')).toBeInTheDocument()
  expect(within(ravi).getByText(/Sunburn Goa/)).toBeInTheDocument()
  const meena = screen.getByText('meena@test.in').closest('li')!
  expect(within(meena).getByText(/not signed in yet/i)).toBeInTheDocument()
  expect(within(meena).getByText(/all events/i)).toBeInTheDocument()
})

test('removing staff asks to confirm', async () => {
  renderPage()
  const ravi = screen.getByText('Ravi').closest('li')!
  await userEvent.click(within(ravi).getByRole('button', { name: /remove/i }))
  expect(remove).not.toHaveBeenCalled()
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /remove/i }))
  expect(remove).toHaveBeenCalledWith('s1')
})
