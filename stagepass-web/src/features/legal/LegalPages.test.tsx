import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({ useCompany: vi.fn() }))
vi.mock('./api', () => hooks)

import { AboutPage } from './AboutPage'
import { PrivacyPage } from './PrivacyPage'
import { RefundsPage } from './RefundsPage'
import { TermsPage } from './TermsPage'

const acme = { name: 'Acme Events Pvt Ltd', address: '12 MG Road, Pune 411001', gstin: null, state: 'Maharashtra', email: 'help@acme.test', phone: null }

beforeEach(() => {
  hooks.useCompany.mockReturnValue({ data: acme, isPending: false })
})

function show(ui: React.ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

test('each legal page has its heading and last-updated date', () => {
  for (const [Page, title] of [
    [AboutPage, 'About & contact'],
    [TermsPage, 'Terms of use'],
    [PrivacyPage, 'Privacy policy'],
    [RefundsPage, 'Refund & cancellation policy'],
  ] as const) {
    const { unmount } = show(<Page />)
    expect(screen.getByRole('heading', { level: 1, name: title })).toBeInTheDocument()
    if (Page !== AboutPage) expect(screen.getByText('Last updated: 8 November 2026')).toBeInTheDocument()
    unmount()
  }
})

test('pages show their text before company details load', () => {
  hooks.useCompany.mockReturnValue({ data: undefined, isPending: true })
  show(<TermsPage />)
  expect(screen.getAllByText(/Stagepass/).length).toBeGreaterThan(0)
  expect(screen.getAllByRole('heading', { level: 2 }).length).toBeGreaterThan(3)
})

test('contact card shows only filled-in details', () => {
  show(<AboutPage />)
  expect(screen.getAllByText('Acme Events Pvt Ltd').length).toBeGreaterThan(0)
  expect(screen.getByText('12 MG Road, Pune 411001')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'help@acme.test' })).toHaveAttribute('href', 'mailto:help@acme.test')
  expect(screen.queryByText(/GSTIN/)).not.toBeInTheDocument()
  expect(screen.queryByText(/Phone/)).not.toBeInTheDocument()
  expect(document.body.textContent).not.toContain('null')
})

test('refund page states the real rules', () => {
  show(<RefundsPage />)
  const text = document.body.textContent ?? ''
  expect(text).toMatch(/refunded in full/i)
  expect(text).toContain('7 days')
  expect(text).toMatch(/convenience fee/i)
  expect(text).toContain('5–7 working days')
  expect(text).toMatch(/checked in|used tickets/i)
})

test('privacy page names every processor', () => {
  show(<PrivacyPage />)
  const text = document.body.textContent ?? ''
  for (const name of ['Supabase', 'Razorpay', 'Cloudinary', 'Resend', 'Render']) expect(text).toContain(name)
  expect(text).toMatch(/encrypted/i)
})
