import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const hooks = vi.hoisted(() => ({ useMyApplication: vi.fn(), useApply: vi.fn() }))
vi.mock('./api', () => hooks)
vi.mock('@/components/upload/ImageUploader', () => ({ ImageUploader: () => <div>uploader</div> }))

import { BecomeOrganizerPage } from './BecomeOrganizerPage'

const application = {
  id: 'a1',
  orgName: 'Acme Live',
  slug: 'acme-live-x1y2z3',
  status: 'REJECTED',
  rejectionReason: 'PAN mismatch',
  contactEmail: 'hello@acme.test',
  contactPhone: '9876543210',
  website: null,
  bio: null,
  logoUrl: null,
  pan: 'ABCDE1234F',
  gstin: null,
  state: 'Maharashtra',
  payoutAccountName: 'Acme Live LLP',
  payoutAccountMasked: '••••9012',
  payoutIfsc: 'HDFC0001234',
  appliedAt: '2026-11-01T10:00:00Z',
  reviewedAt: '2026-11-02T10:00:00Z',
}

const mutate = vi.fn()
beforeEach(() => {
  mutate.mockReset()
  hooks.useApply.mockReturnValue({ mutateAsync: mutate, isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <BecomeOrganizerPage />
    </MemoryRouter>,
  )
}

test('shows under-review state for a pending application', () => {
  hooks.useMyApplication.mockReturnValue({ data: { ...application, status: 'PENDING', rejectionReason: null }, isPending: false, error: null })
  renderPage()
  expect(screen.getByText(/under review/i)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /submit application/i })).not.toBeInTheDocument()
})

test('rejected applicant sees reason and can reapply', async () => {
  hooks.useMyApplication.mockReturnValue({ data: application, isPending: false, error: null })
  mutate.mockResolvedValue({ ...application, status: 'PENDING' })
  renderPage()

  expect(screen.getByText('PAN mismatch')).toBeInTheDocument()
  expect(screen.getByLabelText(/organization name/i)).toHaveValue('Acme Live')

  await userEvent.type(screen.getByLabelText(/bank account number/i), '123456789012')
  await userEvent.click(screen.getByRole('button', { name: /submit application/i }))

  expect(mutate).toHaveBeenCalledWith(expect.objectContaining({ orgName: 'Acme Live', payoutAccountNumber: '123456789012' }))
})

test('first-time visitor sees the empty form', () => {
  hooks.useMyApplication.mockReturnValue({ data: undefined, isPending: false, error: new ApiError(404, 'NOT_FOUND', 'none') })
  renderPage()
  expect(screen.getByRole('button', { name: /submit application/i })).toBeInTheDocument()
  expect(screen.getByLabelText(/organization name/i)).toHaveValue('')
})

test('approved organizer is sent to the dashboard', () => {
  hooks.useMyApplication.mockReturnValue({ data: { ...application, status: 'APPROVED' }, isPending: false, error: null })
  renderPage()
  expect(screen.getByRole('link', { name: /open organizer dashboard/i })).toHaveAttribute('href', '/organizer')
})
