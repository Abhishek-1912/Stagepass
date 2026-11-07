import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const hooks = vi.hoisted(() => ({
  useAdminOrganizer: vi.fn(),
  useSuspendOrganizer: vi.fn(),
  useReinstateOrganizer: vi.fn(),
  useUpdateOrganizer: vi.fn(),
}))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { OrganizerDetailPage } from './OrganizerDetailPage'

const detail = {
  id: 'o1',
  userId: 'u1',
  orgName: 'Acme Live',
  slug: 'acme-live',
  status: 'APPROVED',
  contactEmail: 'hello@acme.test',
  contactPhone: null,
  website: null,
  pan: 'ABCDE1234F',
  gstin: null,
  state: 'Maharashtra',
  payoutAccountName: 'Acme Live LLP',
  payoutAccountMasked: '••••9012',
  payoutIfsc: 'HDFC0001234',
  commissionBpsOverride: null,
  appliedAt: '2026-11-01T10:00:00Z',
  reviewedAt: null,
  balances: { pendingPaise: 40000, payablePaise: -2500, paidOutPaise: 100000, balancePaise: 37500, negative: true },
  payouts: [{ id: 'p1', amountPaise: 100000, reference: 'UTR1', paidAt: '2026-11-02T06:30:00Z', recordedByEmail: 'admin@stagepass.test', note: null }],
}

const update = vi.fn()
beforeEach(() => {
  update.mockReset()
  hooks.useAdminOrganizer.mockReturnValue({ data: detail, isPending: false, isError: false })
  hooks.useSuspendOrganizer.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useReinstateOrganizer.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  hooks.useUpdateOrganizer.mockReturnValue({ mutateAsync: update, isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/organizers/o1']}>
      <Routes>
        <Route path="/admin/organizers/:id" element={<OrganizerDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('organizer detail shows balances and the owed-back flag', () => {
  renderPage()
  expect(screen.getByText('Acme Live')).toBeInTheDocument()
  expect(screen.getByText(/owes stagepass/i)).toBeInTheDocument()
  expect(screen.getByText('UTR1')).toBeInTheDocument()
})

test('organizer detail edits PAN and shows the field error from the API', async () => {
  update.mockRejectedValueOnce(new ApiError(400, 'VALIDATION_FAILED', 'Request validation failed', [{ field: 'pan', message: 'must be a valid PAN like ABCDE1234F' }]))
  renderPage()
  const pan = screen.getByLabelText('PAN')
  await userEvent.clear(pan)
  await userEvent.type(pan, 'abcde12345')
  await userEvent.click(screen.getByRole('button', { name: /save details/i }))
  expect(update).toHaveBeenCalledWith({ id: 'o1', update: { pan: 'ABCDE12345' } })
  expect(await screen.findByText('must be a valid PAN like ABCDE1234F')).toBeInTheDocument()
})
