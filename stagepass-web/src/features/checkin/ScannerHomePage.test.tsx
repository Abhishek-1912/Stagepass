import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, test, vi } from 'vitest'

const api = vi.hoisted(() => ({ useScannerEvents: vi.fn() }))
vi.mock('./api', () => api)

import { ScannerHomePage } from './ScannerHomePage'

function renderPage() {
  return render(
    <MemoryRouter>
      <ScannerHomePage />
    </MemoryRouter>,
  )
}

test('scanner home lists events I can scan', () => {
  api.useScannerEvents.mockReturnValue({
    data: [
      { id: 'e1', title: 'Sunburn Goa', startsAt: '2026-12-20T13:30:00Z', endsAt: '2026-12-20T18:30:00Z', venue: 'Vagator Beach, Goa', organizerName: 'Acme Live', owner: true, gates: [] },
      { id: 'e2', title: 'Comedy Night', startsAt: '2026-12-21T13:30:00Z', endsAt: '2026-12-21T16:30:00Z', venue: 'Online', organizerName: 'Laughs Co', owner: false, gates: [] },
    ],
    isPending: false,
  })
  renderPage()
  expect(screen.getByRole('link', { name: /sunburn goa/i })).toHaveAttribute('href', '/scan/e1')
  expect(screen.getByText('Your event')).toBeInTheDocument()
  expect(screen.getByText('Door staff · Laughs Co')).toBeInTheDocument()
})

test('empty state when not on any door team', () => {
  api.useScannerEvents.mockReturnValue({ data: [], isPending: false })
  renderPage()
  expect(screen.getByText(/not on the door team for any upcoming event/i)).toBeInTheDocument()
})
