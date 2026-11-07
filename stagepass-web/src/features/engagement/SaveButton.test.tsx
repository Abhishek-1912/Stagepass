import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'

const auth = vi.hoisted(() => ({ session: null as object | null }))
vi.mock('@/features/auth/AuthProvider', () => ({ useOptionalAuth: () => ({ session: auth.session }) }))
const client = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('@/lib/api/client', () => client)
const toast = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }))
vi.mock('sonner', () => ({ toast }))

import { SaveButton } from './SaveButton'

function Where() {
  const l = useLocation()
  return <p data-testid="where">{l.pathname + l.search}</p>
}

function wrap(ui: ReactNode, path = '/events/sunburn') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="*" element={<>{ui}<Where /></>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  auth.session = { user: { id: 'u1' } }
  client.api.mockReset()
  toast.error.mockReset()
})

test('save button toggles optimistically and rolls back on error', async () => {
  let fail: (e: Error) => void = () => {}
  client.api.mockImplementation((path: string, init?: { method?: string }) => {
    if (path === '/me/saved/ids') return Promise.resolve([])
    if (init?.method === 'PUT') return new Promise((_, reject) => (fail = reject))
    return Promise.resolve(undefined)
  })
  wrap(<SaveButton eventId="e1" />)
  const button = await screen.findByRole('button', { name: 'Save event' })
  await waitFor(() => expect(client.api).toHaveBeenCalledWith('/me/saved/ids'))

  await userEvent.click(button)
  expect(screen.getByRole('button', { name: 'Remove from saved' })).toHaveAttribute('aria-pressed', 'true')
  expect(client.api).toHaveBeenCalledWith('/me/saved/e1', { method: 'PUT' })

  fail(new Error('network'))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Save event' })).toHaveAttribute('aria-pressed', 'false'))
  expect(toast.error).toHaveBeenCalled()
})

test('already saved events show a filled heart and can be removed', async () => {
  let saved = ['e1']
  client.api.mockImplementation((path: string, init?: { method?: string }) => {
    if (init?.method === 'DELETE') saved = []
    return Promise.resolve(path === '/me/saved/ids' ? saved : undefined)
  })
  wrap(<SaveButton eventId="e1" />)
  const button = await screen.findByRole('button', { name: 'Remove from saved' })
  await userEvent.click(button)
  expect(client.api).toHaveBeenCalledWith('/me/saved/e1', { method: 'DELETE' })
  expect(await screen.findByRole('button', { name: 'Save event' })).toBeInTheDocument()
})

test('a failed save does not undo another event saved meanwhile', async () => {
  let failA: (e: Error) => void = () => {}
  client.api.mockImplementation((path: string, init?: { method?: string }) => {
    if (path === '/me/saved/ids') return Promise.resolve([])
    if (path === '/me/saved/eA' && init?.method === 'PUT') return new Promise((_, reject) => (failA = reject))
    return Promise.resolve(undefined)
  })
  wrap(
    <>
      <div data-testid="a"><SaveButton eventId="eA" /></div>
      <div data-testid="b"><SaveButton eventId="eB" /></div>
    </>,
  )
  await waitFor(() => expect(client.api).toHaveBeenCalledWith('/me/saved/ids'))
  const [a, b] = await screen.findAllByRole('button', { name: 'Save event' })
  await userEvent.click(a)
  await userEvent.click(b)
  await waitFor(() => expect(client.api).toHaveBeenCalledWith('/me/saved/eB', { method: 'PUT' }))

  client.api.mockImplementation((path: string) => Promise.resolve(path === '/me/saved/ids' ? ['eB'] : undefined))
  failA(new Error('gone'))

  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Save event' })).toHaveLength(1))
  expect(screen.getByRole('button', { name: 'Remove from saved' })).toBeInTheDocument()
})

test('signed-out save sends to login with next', async () => {
  auth.session = null
  wrap(<SaveButton eventId="e1" />)
  await userEvent.click(screen.getByRole('button', { name: 'Save event' }))
  expect(screen.getByTestId('where')).toHaveTextContent('/login?next=%2Fevents%2Fsunburn')
  expect(client.api).not.toHaveBeenCalled()
})
