import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const api = vi.hoisted(() => ({ useOrganizationSettings: vi.fn(), useUpdateOrganizationSettings: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('@/components/upload/ImageUploader', () => ({
  ImageUploader: ({ label }: { label: string }) => <div>{label}</div>,
}))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { OrganizationSettingsPage } from './OrganizationSettingsPage'

const settings = {
  orgName: 'Acme Live',
  slug: 'acme-live-x1',
  bio: 'Gigs since 2015',
  logoUrl: null,
  website: null,
  contactEmail: 'hello@acme.in',
  contactPhone: '9876543210',
  pan: 'ABCDE1234F',
  gstin: null,
  payoutAccountName: 'Acme Live LLP',
  payoutAccountMasked: '••••1234',
  payoutIfsc: 'HDFC0001234',
}

const save = vi.fn()
beforeEach(() => {
  save.mockReset().mockResolvedValue(settings)
  api.useOrganizationSettings.mockReturnValue({ data: settings, isPending: false })
  api.useUpdateOrganizationSettings.mockReturnValue({ mutateAsync: save, isPending: false })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <OrganizationSettingsPage />
    </MemoryRouter>,
  )
}

test('settings saves only changed fields', async () => {
  renderPage()
  expect(screen.getByText('Acme Live')).toBeInTheDocument()
  expect(screen.getByText('••••1234')).toBeInTheDocument()

  const bio = screen.getByLabelText(/about/i)
  await userEvent.clear(bio)
  await userEvent.type(bio, 'Live music in Goa')
  await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

  expect(save).toHaveBeenCalledWith({ bio: 'Live music in Goa' })
})

test('payout account change requires both fields', async () => {
  renderPage()
  await userEvent.click(screen.getByRole('button', { name: /change bank account/i }))
  await userEvent.type(screen.getByLabelText(/account number/i), '123456789012')
  await userEvent.clear(screen.getByLabelText(/ifsc/i))
  await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

  expect(await screen.findByText(/valid ifsc/i)).toBeInTheDocument()
  expect(save).not.toHaveBeenCalled()
})

test('server field errors show inline', async () => {
  save.mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', 'Invalid', [{ field: 'website', message: 'must be a full https:// address' }]))
  renderPage()
  await userEvent.type(screen.getByLabelText(/website/i), 'https://acme.in')
  await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

  expect(await screen.findByText('must be a full https:// address')).toBeInTheDocument()
})
