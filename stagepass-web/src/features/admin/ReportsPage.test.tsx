import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({ useAdminReports: vi.fn(), useDecideReport: vi.fn() }))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { ReportsPage } from './ReportsPage'

const report = {
  id: 'r1',
  eventId: 'e1',
  eventTitle: 'Sunburn Goa',
  eventSlug: 'sunburn-goa',
  organizerName: 'Acme Live',
  reason: 'SCAM',
  details: 'Same event listed twice',
  reporterEmail: 'asha@stagepass.test',
  status: 'OPEN',
  resolutionNote: null,
  createdAt: '2026-12-01T10:00:00Z',
  openReportsForEvent: 3,
}
const decide = vi.fn()

beforeEach(() => {
  decide.mockReset().mockResolvedValue({})
  hooks.useAdminReports.mockReturnValue({ data: { items: [report], totalItems: 1, totalPages: 1, page: 0 }, isPending: false })
  hooks.useDecideReport.mockReturnValue({ mutateAsync: decide, isPending: false })
})

test('resolve report needs a note', async () => {
  render(
    <MemoryRouter>
      <ReportsPage />
    </MemoryRouter>,
  )
  expect(screen.getByText('Same event listed twice')).toBeInTheDocument()
  expect(screen.getByText(/3 open reports/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /sunburn goa/i })).toHaveAttribute('href', '/admin/events/e1')

  await userEvent.click(screen.getByRole('button', { name: /^resolve/i }))
  const confirm = screen.getByRole('button', { name: 'Mark resolved' })
  expect(confirm).toBeDisabled()
  await userEvent.type(screen.getByLabelText(/reason/i), 'Organizer fixed the listing')
  await userEvent.click(confirm)
  expect(decide).toHaveBeenCalledWith({ id: 'r1', action: 'resolve', note: 'Organizer fixed the listing' })
})
