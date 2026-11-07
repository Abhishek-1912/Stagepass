import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useGates: vi.fn(), useCreateGate: vi.fn(), useUpdateGate: vi.fn(), useDeleteGate: vi.fn() }))
vi.mock('@/features/checkin/api', () => api)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { GatesEditor } from './GatesEditor'

const create = vi.fn()
const del = vi.fn()
const types = [
  { id: 't-gen', name: 'General' },
  { id: 't-vip', name: 'VIP' },
]

beforeEach(() => {
  create.mockReset().mockResolvedValue({})
  del.mockReset().mockResolvedValue(undefined)
  api.useGates.mockReturnValue({ data: [{ id: 'g1', name: 'Main Gate', ticketTypeIds: [], checkedIn: 12 }], isPending: false })
  api.useCreateGate.mockReturnValue({ mutateAsync: create, isPending: false })
  api.useUpdateGate.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  api.useDeleteGate.mockReturnValue({ mutateAsync: del, isPending: false })
})

test('lists gates with what they accept', () => {
  render(<GatesEditor eventId="e1" ticketTypes={types} />)
  const main = screen.getByText('Main Gate').closest('li')!
  expect(within(main).getByText(/all ticket types/i)).toBeInTheDocument()
  expect(within(main).getByText(/12 checked in/i)).toBeInTheDocument()
})

test('gate can be limited to ticket types', async () => {
  render(<GatesEditor eventId="e1" ticketTypes={types} />)
  await userEvent.type(screen.getByLabelText(/gate name/i), 'VIP Gate')
  await userEvent.click(screen.getByRole('checkbox', { name: 'VIP' }))
  await userEvent.click(screen.getByRole('button', { name: /add gate/i }))
  expect(create).toHaveBeenCalledWith({ name: 'VIP Gate', ticketTypeIds: ['t-vip'] })
})

test('deleting a gate confirms and keeps history', async () => {
  render(<GatesEditor eventId="e1" ticketTypes={types} />)
  await userEvent.click(screen.getByRole('button', { name: /delete main gate/i }))
  expect(screen.getByRole('dialog')).toHaveTextContent(/history is kept/i)
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /delete gate/i }))
  expect(del).toHaveBeenCalledWith('g1')
})
