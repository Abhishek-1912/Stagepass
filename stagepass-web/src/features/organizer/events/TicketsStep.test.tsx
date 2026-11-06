import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'
import { eventDetail, ticketType } from './testEvent'

const hooks = vi.hoisted(() => ({ useSaveTicketType: vi.fn(), useDeleteTicketType: vi.fn(), useUpdateEvent: vi.fn() }))
vi.mock('../api', () => hooks)
const toast = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }))
vi.mock('sonner', () => ({ toast }))

import { TicketsStep } from './steps/TicketsStep'

const save = vi.fn()
beforeEach(() => {
  save.mockReset()
  toast.error.mockReset()
  hooks.useSaveTicketType.mockReturnValue({ mutateAsync: save, isPending: false })
  hooks.useDeleteTicketType.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useUpdateEvent.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

test('lists ticket types with price and availability', () => {
  render(<TicketsStep event={eventDetail({ ticketTypes: [ticketType(), ticketType({ id: 't2', name: 'VIP', pricePaise: 149900, quantity: 50, available: 50 })] })} />)
  expect(screen.getByText('₹499')).toBeInTheDocument()
  expect(screen.getByText('₹1,499')).toBeInTheDocument()
  expect(screen.getByText('VIP')).toBeInTheDocument()
})

test('locks price after first sale', async () => {
  render(<TicketsStep event={eventDetail({ ticketTypes: [ticketType({ sold: 3, available: 297 })] })} />)

  await userEvent.click(screen.getByRole('button', { name: /edit general/i }))

  const dialog = await screen.findByRole('dialog')
  expect(within(dialog).getByLabelText(/price/i)).toBeDisabled()
  expect(within(dialog).getByText(/price is locked/i)).toBeInTheDocument()
})

test('shows the server minimum when quantity is too low', async () => {
  save.mockRejectedValue(new ApiError(409, 'CONFLICT', 'You can’t go below 45 tickets — that many are already sold or reserved.'))
  render(<TicketsStep event={eventDetail({ ticketTypes: [ticketType({ sold: 40, held: 5, available: 255 })] })} />)

  await userEvent.click(screen.getByRole('button', { name: /edit general/i }))
  const dialog = await screen.findByRole('dialog')
  const quantity = within(dialog).getByLabelText(/quantity/i)
  await userEvent.clear(quantity)
  await userEvent.type(quantity, '30')
  await userEvent.click(within(dialog).getByRole('button', { name: /save ticket type/i }))

  expect(save).toHaveBeenCalledWith({ id: 't1', input: expect.objectContaining({ quantity: 30 }) })
  expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('45'))
})

test('emptying max tickets per person clears the limit', async () => {
  const updateEvent = vi.fn().mockResolvedValue(eventDetail())
  hooks.useUpdateEvent.mockReturnValue({ mutateAsync: updateEvent, isPending: false })
  render(<TicketsStep event={eventDetail({ maxTicketsPerUser: 4 })} />)

  await userEvent.clear(screen.getByLabelText(/max tickets per person/i))
  await userEvent.click(screen.getByRole('button', { name: /save rules/i }))

  expect(updateEvent).toHaveBeenCalledWith(expect.objectContaining({ clear: expect.arrayContaining(['maxTicketsPerUser']) }))
  expect(updateEvent.mock.calls[0][0]).not.toHaveProperty('maxTicketsPerUser')
})

test('refund window needs a whole number of hours', async () => {
  const updateEvent = vi.fn()
  hooks.useUpdateEvent.mockReturnValue({ mutateAsync: updateEvent, isPending: false })
  render(<TicketsStep event={eventDetail({ refundPolicy: 'UNTIL_HOURS_BEFORE', refundCutoffHours: 48 })} />)

  const hours = screen.getByLabelText(/hours before start/i)
  await userEvent.clear(hours)
  await userEvent.type(hours, 'abc')
  await userEvent.click(screen.getByRole('button', { name: /save rules/i }))

  expect(updateEvent).not.toHaveBeenCalled()
  expect(toast.error).toHaveBeenCalledWith(expect.stringMatching(/hours/i))
})

test('adds a new ticket type in paise', async () => {
  save.mockResolvedValue(ticketType({ id: 't9', name: 'Early bird' }))
  render(<TicketsStep event={eventDetail({ ticketTypes: [] })} />)

  await userEvent.click(screen.getByRole('button', { name: /add ticket type/i }))
  const dialog = await screen.findByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Early bird')
  await userEvent.type(within(dialog).getByLabelText(/price/i), '299')
  await userEvent.type(within(dialog).getByLabelText(/quantity/i), '100')
  await userEvent.click(within(dialog).getByRole('button', { name: /save ticket type/i }))

  expect(save).toHaveBeenCalledWith({ id: undefined, input: expect.objectContaining({ name: 'Early bird', pricePaise: 29900, quantity: 100 }) })
})
