import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { ConfirmAction } from './ConfirmAction'

test('confirm dialog restates the effect and blocks double submit', async () => {
  let finish: () => void = () => {}
  const onConfirm = vi.fn(() => new Promise<void>((resolve) => (finish = resolve)))
  render(
    <ConfirmAction
      trigger={<button type="button">Suspend</button>}
      title="Suspend Asha?"
      effect="They are signed out of Stagepass on their next request."
      confirmLabel="Suspend account"
      requireReason
      onConfirm={onConfirm}
    />,
  )

  await userEvent.click(screen.getByRole('button', { name: 'Suspend' }))
  expect(screen.getByText('They are signed out of Stagepass on their next request.')).toBeInTheDocument()
  const confirm = screen.getByRole('button', { name: 'Suspend account' })
  expect(confirm).toBeDisabled()

  await userEvent.type(screen.getByLabelText(/reason/i), 'no')
  expect(confirm).toBeDisabled()
  await userEvent.type(screen.getByLabelText(/reason/i), 't ok')
  expect(confirm).toBeEnabled()

  await userEvent.click(confirm)
  expect(onConfirm).toHaveBeenCalledWith('not ok')
  expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: /saving/i }))
  expect(onConfirm).toHaveBeenCalledTimes(1)
  finish()
})

test('without a reason the confirm button is ready at once', async () => {
  const onConfirm = vi.fn().mockResolvedValue(undefined)
  render(
    <ConfirmAction trigger={<button type="button">Reinstate</button>} title="Reinstate?" effect="Events show again."
      confirmLabel="Reinstate organizer" onConfirm={onConfirm} />,
  )
  await userEvent.click(screen.getByRole('button', { name: 'Reinstate' }))
  await userEvent.click(screen.getByRole('button', { name: 'Reinstate organizer' }))
  expect(onConfirm).toHaveBeenCalledWith('')
})
