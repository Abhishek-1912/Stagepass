import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const me = vi.hoisted(() => ({ useMe: vi.fn() }))
vi.mock('./useMe', () => me)

import { ApiError } from '@/lib/api/ApiError'
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

function renderWithError(code: string, status: number) {
  me.useMe.mockReturnValue({ data: undefined, isPending: false, isError: true, error: new ApiError(status, code, 'x') })
  return render(
    <MemoryRouter>
      <RequireRole roles={['ATTENDEE']}>
        <p>Area</p>
      </RequireRole>
    </MemoryRouter>,
  )
}

test('explains a suspended account', () => {
  renderWithError('ACCOUNT_SUSPENDED', 403)
  expect(screen.getByText(/account has been suspended/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /contact support/i })).toBeInTheDocument()
})

test('explains an email already used by another account', () => {
  renderWithError('ACCOUNT_CONFLICT', 409)
  expect(screen.getByText(/already linked to another stagepass account/i)).toBeInTheDocument()
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
