import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const client = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('@/lib/api/client', () => client)

import { ReportEventDialog } from './ReportEventDialog'

beforeEach(() => {
  client.api.mockReset().mockResolvedValue({ id: 'r1', status: 'OPEN' })
})

test('report dialog requires details for Other', async () => {
  render(<ReportEventDialog eventId="e1" />)
  await userEvent.click(screen.getByRole('button', { name: /report this event/i }))

  await userEvent.click(screen.getByLabelText(/something else/i))
  const send = screen.getByRole('button', { name: 'Send report' })
  expect(send).toBeDisabled()
  await userEvent.type(screen.getByLabelText(/details/i), 'Organizer asks for payment on WhatsApp')
  await userEvent.click(send)

  expect(client.api).toHaveBeenCalledWith('/events/e1/reports', {
    method: 'POST',
    json: { reason: 'OTHER', details: 'Organizer asks for payment on WhatsApp' },
  })
  expect(await screen.findByText(/thanks — our team will review it/i)).toBeInTheDocument()
})

test('a simple reason can be sent without details', async () => {
  render(<ReportEventDialog eventId="e1" />)
  await userEvent.click(screen.getByRole('button', { name: /report this event/i }))
  await userEvent.click(screen.getByLabelText(/looks like a scam/i))
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }))
  expect(client.api).toHaveBeenCalledWith('/events/e1/reports', { method: 'POST', json: { reason: 'SCAM' } })
})
