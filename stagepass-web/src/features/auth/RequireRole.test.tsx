import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const me = vi.hoisted(() => ({ useMe: vi.fn() }))
vi.mock('./useMe', () => me)

import { RequireRole } from './RequireRole'

function renderGuard(roles: string[]) {
  me.useMe.mockReturnValue({ data: { roles }, isPending: false, isError: false })
  return render(
    <MemoryRouter>
      <RequireRole roles={['ORGANIZER']}>
        <p>Organizer area</p>
      </RequireRole>
    </MemoryRouter>,
  )
}

beforeEach(() => me.useMe.mockReset())

test('renders children for matching role', () => {
  renderGuard(['ATTENDEE', 'ORGANIZER'])
  expect(screen.getByText('Organizer area')).toBeInTheDocument()
})

test('shows forbidden for missing role', () => {
  renderGuard(['ATTENDEE'])
  expect(screen.queryByText('Organizer area')).not.toBeInTheDocument()
  expect(screen.getByText(/don’t have access/i)).toBeInTheDocument()
})

test('shows a loader while the profile loads', () => {
  me.useMe.mockReturnValue({ data: undefined, isPending: true, isError: false })
  render(
    <MemoryRouter>
      <RequireRole roles={['ADMIN']}>
        <p>Admin area</p>
      </RequireRole>
    </MemoryRouter>,
  )
  expect(screen.getByRole('status')).toBeInTheDocument()
  expect(screen.queryByText('Admin area')).not.toBeInTheDocument()
})
