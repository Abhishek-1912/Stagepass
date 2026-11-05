import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const auth = vi.hoisted(() => ({ signInWithOtp: vi.fn(), verifyOtp: vi.fn() }))
vi.mock('@/lib/supabase', () => ({ supabase: { auth } }))

import { OtpLoginForm } from './OtpLoginForm'

beforeEach(() => {
  auth.signInWithOtp.mockReset().mockResolvedValue({ data: {}, error: null })
  auth.verifyOtp.mockReset().mockResolvedValue({ data: { session: {} }, error: null })
})

async function requestCode(email = 'attendee@stagepass.test') {
  const user = userEvent.setup()
  const onSignedIn = vi.fn()
  render(<OtpLoginForm onSignedIn={onSignedIn} />)
  await user.type(screen.getByLabelText(/email/i), email)
  await user.click(screen.getByRole('button', { name: /email me a code/i }))
  return { user, onSignedIn }
}

test('sends a sign-in code that also creates new accounts', async () => {
  await requestCode()

  expect(auth.signInWithOtp).toHaveBeenCalledWith({
    email: 'attendee@stagepass.test',
    options: { shouldCreateUser: true },
  })
  expect(await screen.findByText(/sent a 6-digit code to/i)).toBeInTheDocument()
})

test('verifies the code and signs in', async () => {
  const { user, onSignedIn } = await requestCode()

  await user.type(await screen.findByLabelText(/6-digit code/i), '482913')
  await user.click(screen.getByRole('button', { name: /verify and sign in/i }))

  expect(auth.verifyOtp).toHaveBeenCalledWith({ email: 'attendee@stagepass.test', token: '482913', type: 'email' })
  expect(onSignedIn).toHaveBeenCalled()
})

test('shows a clear error for a wrong or expired code', async () => {
  auth.verifyOtp.mockResolvedValue({ data: { session: null }, error: new Error('Token has expired or is invalid') })
  const { user, onSignedIn } = await requestCode()

  await user.type(await screen.findByLabelText(/6-digit code/i), '000000')
  await user.click(screen.getByRole('button', { name: /verify and sign in/i }))

  expect(await screen.findByRole('alert')).toHaveTextContent(/incorrect or has expired/i)
  expect(onSignedIn).not.toHaveBeenCalled()
})

test('rejects codes that are not 6 digits before calling Supabase', async () => {
  const { user } = await requestCode()

  await user.type(await screen.findByLabelText(/6-digit code/i), '12a4')
  await user.click(screen.getByRole('button', { name: /verify and sign in/i }))

  expect(await screen.findByText(/enter the 6-digit code/i)).toBeInTheDocument()
  expect(auth.verifyOtp).not.toHaveBeenCalled()
})

test('explains rate limiting when too many codes are requested', async () => {
  auth.signInWithOtp.mockResolvedValue({ data: {}, error: Object.assign(new Error('rate limit'), { status: 429 }) })
  await requestCode()

  expect(await screen.findByRole('alert')).toHaveTextContent(/too many codes/i)
})

test('resend is disabled during the cooldown', async () => {
  await requestCode()

  expect(await screen.findByRole('button', { name: /resend code in/i })).toBeDisabled()
})
