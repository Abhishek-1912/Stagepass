import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const hooks = vi.hoisted(() => ({ useSaveReview: vi.fn(), useDeleteReview: vi.fn() }))
vi.mock('./reviewsApi', () => hooks)
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

import { ReviewDialog } from './ReviewDialog'

const save = vi.fn()

beforeEach(() => {
  save.mockReset().mockResolvedValue({})
  hooks.useSaveReview.mockReturnValue({ mutateAsync: save, isPending: false })
  hooks.useDeleteReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
})

test('dialog sends rating and comment', async () => {
  render(<ReviewDialog eventId="e1" existing={null} />)
  await userEvent.click(screen.getByRole('button', { name: /rate this event/i }))
  const post = screen.getByRole('button', { name: 'Post review' })
  expect(post).toBeDisabled()
  await userEvent.click(screen.getByLabelText('4 stars'))
  await userEvent.type(screen.getByLabelText(/comment/i), 'Great sound, long queue')
  await userEvent.click(post)
  expect(save).toHaveBeenCalledWith({ eventId: 'e1', rating: 4, comment: 'Great sound, long queue' })
})

test('editing an existing review starts from it', async () => {
  render(<ReviewDialog eventId="e1" existing={{ id: 'r1', rating: 2, comment: 'Meh', authorName: 'Asha', createdAt: '', updatedAt: '' }} />)
  await userEvent.click(screen.getByRole('button', { name: /edit your review/i }))
  expect(screen.getByLabelText('2 stars')).toBeChecked()
  expect(screen.getByLabelText(/comment/i)).toHaveValue('Meh')
  await userEvent.click(screen.getByLabelText('5 stars'))
  await userEvent.click(screen.getByRole('button', { name: 'Save review' }))
  expect(save).toHaveBeenCalledWith({ eventId: 'e1', rating: 5, comment: 'Meh' })
})
