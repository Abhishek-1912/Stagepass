import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({
  useAdminUsers: vi.fn(),
  useSuspendUser: vi.fn(),
  useReactivateUser: vi.fn(),
  useSetAdmin: vi.fn(),
}))
vi.mock('./api', () => hooks)
vi.mock('@/features/auth/useMe', () => ({ useMe: () => ({ data: { id: 'me', roles: ['ADMIN'] } }) }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { UsersPage } from './UsersPage'

const asha = {
  id: 'u1',
  email: 'asha@stagepass.test',
  fullName: 'Asha Rao',
  roles: ['ATTENDEE'],
  status: 'ACTIVE',
  createdAt: '2026-11-01T10:00:00Z',
  suspendedAt: null,
  suspensionReason: null,
}
const me = { ...asha, id: 'me', email: 'admin@stagepass.test', fullName: 'Admin', roles: ['ADMIN', 'ATTENDEE'] }

const suspend = vi.fn()
beforeEach(() => {
  suspend.mockReset().mockResolvedValue({ ...asha, status: 'SUSPENDED' })
  hooks.useAdminUsers.mockReturnValue({ data: { items: [asha, me], totalItems: 2, totalPages: 1, page: 0 }, isPending: false })
  hooks.useSuspendUser.mockReturnValue({ mutateAsync: suspend, isPending: false })
  hooks.useReactivateUser.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useSetAdmin.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

test('suspending a user needs a reason and calls the API', async () => {
  render(<UsersPage />)
  const row = screen.getByRole('row', { name: /asha rao/i })
  await userEvent.click(within(row).getByRole('button', { name: /suspend/i }))
  const confirm = screen.getByRole('button', { name: 'Suspend account' })
  await userEvent.type(screen.getByLabelText(/reason/i), 'ab')
  expect(confirm).toBeDisabled()
  await userEvent.type(screen.getByLabelText(/reason/i), 'use of stolen cards')
  await userEvent.click(confirm)
  expect(suspend).toHaveBeenCalledWith({ id: 'u1', reason: 'abuse of stolen cards' })
})

test('suspended users show their status and reason', () => {
  hooks.useAdminUsers.mockReturnValue({
    data: { items: [{ ...asha, status: 'SUSPENDED', suspensionReason: 'Chargebacks' }], totalItems: 1, totalPages: 1, page: 0 },
    isPending: false,
  })
  render(<UsersPage />)
  const row = screen.getByRole('row', { name: /asha rao/i })
  expect(within(row).getByText('Suspended')).toBeInTheDocument()
  expect(within(row).getByText('Chargebacks')).toBeInTheDocument()
  expect(within(row).getByRole('button', { name: /reactivate/i })).toBeInTheDocument()
})

test('own row has no suspend or remove-admin actions', () => {
  render(<UsersPage />)
  const row = screen.getByRole('row', { name: /admin@stagepass.test/i })
  expect(within(row).queryByRole('button', { name: /suspend/i })).not.toBeInTheDocument()
  expect(within(row).queryByRole('button', { name: /remove admin/i })).not.toBeInTheDocument()
  expect(within(row).getByText(/you/i)).toBeInTheDocument()
})
