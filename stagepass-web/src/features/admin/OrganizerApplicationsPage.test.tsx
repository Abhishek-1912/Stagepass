import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({
  useAdminApplications: vi.fn(),
  useApproveApplication: vi.fn(),
  useRejectApplication: vi.fn(),
}))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { OrganizerApplicationsPage } from './OrganizerApplicationsPage'

const application = {
  id: 'a1',
  userId: 'u1',
  orgName: 'Acme Live',
  slug: 'acme-live-x1',
  status: 'PENDING',
  rejectionReason: null,
  contactEmail: 'hello@acme.test',
  contactPhone: '9876543210',
  website: null,
  bio: 'Gigs in Pune',
  logoUrl: null,
  pan: 'ABCDE1234F',
  gstin: null,
  state: 'Maharashtra',
  payoutAccountName: 'Acme Live LLP',
  payoutAccountMasked: '••••9012',
  payoutIfsc: 'HDFC0001234',
  appliedAt: '2026-11-01T10:00:00Z',
  reviewedAt: null,
}

const approve = vi.fn()
beforeEach(() => {
  approve.mockReset().mockResolvedValue({ ...application, status: 'APPROVED' })
  hooks.useAdminApplications.mockReturnValue({ data: { items: [application], totalItems: 1 }, isPending: false })
  hooks.useApproveApplication.mockReturnValue({ mutateAsync: approve, isPending: false })
  hooks.useRejectApplication.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

test('shows application details with masked account', () => {
  render(<OrganizerApplicationsPage />)
  expect(screen.getByText('Acme Live')).toBeInTheDocument()
  expect(screen.getByText('ABCDE1234F')).toBeInTheDocument()
  expect(screen.getByText(/••••9012/)).toBeInTheDocument()
})

test('approving an application calls the API', async () => {
  render(<OrganizerApplicationsPage />)

  await userEvent.click(screen.getByRole('button', { name: /approve acme live/i }))

  expect(approve).toHaveBeenCalledWith('a1')
})

test('pending tab requests pending applications', () => {
  render(<OrganizerApplicationsPage />)
  expect(hooks.useAdminApplications).toHaveBeenCalledWith('PENDING')
})
