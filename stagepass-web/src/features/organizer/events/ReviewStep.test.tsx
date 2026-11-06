import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'
import { eventDetail } from './testEvent'

const hooks = vi.hoisted(() => ({ useSubmitEvent: vi.fn(), useCancelEvent: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })) }))
vi.mock('../api', () => hooks)
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

import { ReviewStep } from './steps/ReviewStep'

const submit = vi.fn()
beforeEach(() => {
  submit.mockReset()
  hooks.useSubmitEvent.mockReturnValue({ mutateAsync: submit, isPending: false })
})

const renderStep = (event = eventDetail()) =>
  render(
    <MemoryRouter>
      <ReviewStep event={event} />
    </MemoryRouter>,
  )

test('complete draft can be submitted', async () => {
  submit.mockResolvedValue(eventDetail({ status: 'PENDING_REVIEW' }))
  renderStep()

  await userEvent.click(screen.getByRole('button', { name: /submit for review/i }))
  expect(submit).toHaveBeenCalled()
})

test('checklist flags missing cover locally', () => {
  renderStep(eventDetail({ coverImageUrl: null }))
  expect(screen.getByRole('listitem', { name: /add a cover image/i })).toHaveAttribute('data-done', 'false')
  expect(screen.getByRole('button', { name: /submit for review/i })).toBeDisabled()
})

test('maps server submit errors onto the checklist', async () => {
  submit.mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', 'Request validation failed', [{ field: 'startsAt', message: 'Start time must be in the future' }]))
  renderStep()

  await userEvent.click(screen.getByRole('button', { name: /submit for review/i }))

  expect(await screen.findByRole('listitem', { name: /start time is in the future/i })).toHaveAttribute('data-done', 'false')
})

test('shows rejection reason', () => {
  renderStep(eventDetail({ status: 'REJECTED', rejectionReason: 'Add venue photos' }))
  expect(screen.getByText('Add venue photos')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /resubmit for review/i })).toBeInTheDocument()
})

test('pending event shows waiting state instead of submit', () => {
  renderStep(eventDetail({ status: 'PENDING_REVIEW', submittedAt: '2026-11-06T10:00:00Z' }))
  expect(screen.getByText(/waiting for review/i)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /submit for review/i })).not.toBeInTheDocument()
})
