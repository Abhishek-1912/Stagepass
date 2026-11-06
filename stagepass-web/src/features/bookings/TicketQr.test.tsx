import { render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'

const qrcode = vi.hoisted(() => ({ toDataURL: vi.fn(async () => 'data:image/png;base64,QR') }))
vi.mock('qrcode', () => ({ default: qrcode, ...qrcode }))

import { TicketQr } from './TicketQr'

test('renders a QR image for the token', async () => {
  render(<TicketQr token="tok.sig" size={240} label="QR code for SP-AAAA-2222" />)

  const img = await screen.findByRole('img', { name: 'QR code for SP-AAAA-2222' })
  expect(img).toHaveAttribute('src', 'data:image/png;base64,QR')
  expect(qrcode.toDataURL).toHaveBeenCalledWith('tok.sig', expect.objectContaining({ errorCorrectionLevel: 'M', margin: 1 }))
})
