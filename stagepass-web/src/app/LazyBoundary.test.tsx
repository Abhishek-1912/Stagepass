import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { LazyBoundary } from './LazyBoundary'

function Boom({ error }: { error: Error }): never {
  throw error
}

afterEach(() => vi.restoreAllMocks())

test('a failed chunk load offers a reload', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  const reload = vi.fn()
  render(
    <LazyBoundary onReload={reload}>
      <Boom error={new TypeError('Failed to fetch dynamically imported module: /assets/AdminLayout-abc.js')} />
    </LazyBoundary>,
  )
  expect(screen.getByText(/a new version of stagepass is available/i)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /reload/i }))
  expect(reload).toHaveBeenCalled()
})

test('other errors are not swallowed', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  expect(() =>
    render(
      <LazyBoundary onReload={vi.fn()}>
        <Boom error={new Error('boom')} />
      </LazyBoundary>,
    ),
  ).toThrow('boom')
})
