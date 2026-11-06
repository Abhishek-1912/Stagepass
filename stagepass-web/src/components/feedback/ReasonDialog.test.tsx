import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { ReasonDialog } from './ReasonDialog'

test('requires a reason before confirming', async () => {
  const onConfirm = vi.fn().mockResolvedValue(undefined)
  render(
    <ReasonDialog
      trigger={<button>Reject</button>}
      title="Reject application"
      description="The organizer will see this."
      confirmLabel="Reject"
      onConfirm={onConfirm}
    />,
  )

  await userEvent.click(screen.getByRole('button', { name: 'Reject' }))
  const confirm = screen.getAllByRole('button', { name: 'Reject' }).at(-1)!
  expect(confirm).toBeDisabled()

  await userEvent.type(screen.getByLabelText(/reason/i), '   ')
  expect(confirm).toBeDisabled()

  await userEvent.type(screen.getByLabelText(/reason/i), 'PAN mismatch')
  await userEvent.click(confirm)
  expect(onConfirm).toHaveBeenCalledWith('PAN mismatch')
})
