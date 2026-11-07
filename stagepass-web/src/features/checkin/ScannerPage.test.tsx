import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'

const api = vi.hoisted(() => ({ useScannerEvents: vi.fn(), scanTicket: vi.fn() }))
vi.mock('./api', () => api)
vi.mock('./CameraScanner', () => ({
  CameraScanner: ({ onCode, paused }: { onCode: (t: string) => void; paused: boolean }) => (
    <button type="button" disabled={paused} onClick={() => onCode('tok')}>
      Simulate scan
    </button>
  ),
}))

import { ScannerPage } from './ScannerPage'

const counts = { checkedIn: 1, total: 20 }
const valid = { result: 'VALID', message: 'Welcome in', ticket: { code: 'SP-AAAA-2222', holderName: 'Asha Rao', ticketType: 'General' }, counts }

function event(gates: { id: string; name: string; ticketTypeNames: string[] }[]) {
  return { id: 'e1', title: 'Sunburn Goa', startsAt: '2026-12-20T13:30:00Z', endsAt: '2026-12-20T18:30:00Z', venue: 'Goa', organizerName: 'Acme', owner: true, gates }
}

beforeEach(() => {
  api.scanTicket.mockReset().mockResolvedValue(valid)
  api.useScannerEvents.mockReturnValue({ data: [event([])], isPending: false })
})
afterEach(() => vi.useRealTimers())

function renderAt(path = '/scan/e1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/scan/:eventId" element={<ScannerPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('picking a gate then scanning shows the result', async () => {
  api.useScannerEvents.mockReturnValue({ data: [event([{ id: 'g1', name: 'Gate 1', ticketTypeNames: [] }, { id: 'g2', name: 'VIP Gate', ticketTypeNames: ['VIP'] }])], isPending: false })
  renderAt()

  expect(screen.queryByRole('button', { name: 'Simulate scan' })).not.toBeInTheDocument()
  expect(screen.getByText('VIP only')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /gate 1/i }))
  await userEvent.click(screen.getByRole('button', { name: 'Simulate scan' }))

  expect(api.scanTicket).toHaveBeenCalledWith({ eventId: 'e1', gateId: 'g1', token: 'tok' })
  expect(await screen.findByText('Welcome in')).toBeInTheDocument()
  expect(screen.getByText('Asha Rao')).toBeInTheDocument()
  expect(screen.getByText('1 / 20')).toBeInTheDocument()
})

test('manual code entry scans by code', async () => {
  renderAt()
  await userEvent.type(screen.getByLabelText(/ticket code/i), 'sp-aaaa-2222')
  await userEvent.click(screen.getByRole('button', { name: /^check$/i }))
  expect(api.scanTicket).toHaveBeenCalledWith({ eventId: 'e1', gateId: null, code: 'sp-aaaa-2222' })
})

test('result clears after 2.5 s and on tap', async () => {
  vi.useFakeTimers()
  renderAt()
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Simulate scan' }))
  })
  expect(screen.getByText('Welcome in')).toBeInTheDocument()
  await act(async () => {
    vi.advanceTimersByTime(2500)
  })
  expect(screen.queryByText('Welcome in')).not.toBeInTheDocument()

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Simulate scan' }))
  })
  fireEvent.click(screen.getByRole('status'))
  expect(screen.queryByText('Welcome in')).not.toBeInTheDocument()
})

test('access lost shows a clear message', async () => {
  api.scanTicket.mockRejectedValue(new ApiError(404, 'NOT_FOUND', 'Event not found'))
  renderAt()
  await userEvent.click(screen.getByRole('button', { name: 'Simulate scan' }))
  expect(await screen.findByText(/you no longer have access to this event's door/i)).toBeInTheDocument()
})

test('event not in my list says so', () => {
  api.useScannerEvents.mockReturnValue({ data: [], isPending: false })
  renderAt()
  expect(screen.getByText(/you no longer have access to this event's door/i)).toBeInTheDocument()
})
