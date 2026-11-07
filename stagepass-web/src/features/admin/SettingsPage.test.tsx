import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { feePreview } from './feeMath'

const hooks = vi.hoisted(() => ({ useFeeSettings: vi.fn(), useSaveFeeSettings: vi.fn() }))
vi.mock('./api', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { SettingsPage } from './SettingsPage'

const defaults = { convenienceFeeBps: 250, convenienceFeeFlatPaise: 1000, gstBps: 1800, commissionBps: 0, refundFeeOnAttendeeRequest: false }
const save = vi.fn()

beforeEach(() => {
  save.mockReset().mockResolvedValue(defaults)
  hooks.useFeeSettings.mockReturnValue({ data: defaults, isPending: false, isError: false })
  hooks.useSaveFeeSettings.mockReturnValue({ mutateAsync: save, isPending: false })
})

test('fee preview matches the server formula', () => {
  // ₹1,000 × 2: 2.5% of ₹2,000 = ₹50 + ₹10 × 2 = ₹70 fee, 18% GST = ₹12.60
  expect(feePreview(defaults, 100000, 2)).toEqual({ subtotal: 200000, fee: 7000, gst: 1260, total: 208260 })
  expect(feePreview({ ...defaults, convenienceFeeBps: 333 }, 49900, 1)).toEqual({ subtotal: 49900, fee: 2662, gst: 479, total: 53041 })
  expect(feePreview(defaults, 0, 3)).toEqual({ subtotal: 0, fee: 0, gst: 0, total: 0 })
})

test('settings converts percent to bps and previews the fee', async () => {
  render(<SettingsPage />)
  expect(screen.getByText(/₹2,082.60/)).toBeInTheDocument()

  const pct = screen.getByLabelText(/convenience fee \(%\)/i)
  await userEvent.clear(pct)
  await userEvent.type(pct, '5')
  expect(screen.getByText(/₹2,141.60/)).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: /save fees/i }))
  expect(save).toHaveBeenCalledWith({ ...defaults, convenienceFeeBps: 500 })
})

test('out-of-range values are blocked', async () => {
  render(<SettingsPage />)
  const pct = screen.getByLabelText(/convenience fee \(%\)/i)
  await userEvent.clear(pct)
  await userEvent.type(pct, '25')
  await userEvent.click(screen.getByRole('button', { name: /save fees/i }))
  expect(save).not.toHaveBeenCalled()
  expect(screen.getByText(/between 0 and 20/i)).toBeInTheDocument()
})
