import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from './ThemeProvider'
import { ThemeToggle } from './ThemeToggle'

test('toggle switches to dark and persists', async () => {
  render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  )

  await userEvent.click(screen.getByRole('button', { name: /toggle theme/i }))

  expect(document.documentElement).toHaveClass('dark')
  expect(localStorage.getItem('stagepass-theme')).toBe('dark')
})

test('restores saved dark theme on load', () => {
  localStorage.setItem('stagepass-theme', 'dark')

  render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  )

  expect(document.documentElement).toHaveClass('dark')
})
