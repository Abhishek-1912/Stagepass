import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('sonner', () => ({ toast }))

import { ShareButton } from './ShareButton'

const url = 'http://api.test/e/sunburn-goa-abc123'

afterEach(() => {
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'share')
})

test('offers the system share sheet alongside copy link when available', async () => {
  const share = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'share', { value: share, configurable: true })
  render(<ShareButton url={url} title="Sunburn Goa" />)

  await userEvent.click(screen.getByRole('button', { name: /share/i }))
  expect(await screen.findByRole('menuitem', { name: /copy link/i })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('menuitem', { name: /more options/i }))

  expect(share).toHaveBeenCalledWith({ title: 'Sunburn Goa', text: 'Sunburn Goa on Stagepass', url })
})

test('falls back to WhatsApp, X and copy link', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
  render(<ShareButton url={url} title="Sunburn & Friends" />)

  await userEvent.click(screen.getByRole('button', { name: /share/i }))

  const whatsapp = await screen.findByRole('menuitem', { name: /whatsapp/i })
  expect(whatsapp.closest('a') ?? whatsapp).toHaveAttribute(
    'href',
    `https://wa.me/?text=${encodeURIComponent(`Sunburn & Friends ${url}`)}`,
  )
  expect(screen.queryByRole('menuitem', { name: /more options/i })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('menuitem', { name: /copy link/i }))
  expect(writeText).toHaveBeenCalledWith(url)
  expect(toast.success).toHaveBeenCalled()
})
