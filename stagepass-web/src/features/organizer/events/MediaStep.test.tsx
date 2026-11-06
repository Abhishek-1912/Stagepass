import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { expect, test, vi } from 'vitest'
import { eventDetail } from './testEvent'

vi.mock('../api', () => ({
  useUpdateEvent: () => ({ mutateAsync: vi.fn(), isPending: false }),
  requestUploadSignature: vi.fn(),
}))
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

import { MediaStep } from './steps/MediaStep'

test('cover can be replaced but not removed; gallery photos can be removed', () => {
  render(
    <MemoryRouter>
      <MediaStep event={eventDetail({ gallery: ['https://res.cloudinary.com/demo/image/upload/stagepass/events/e1/g1.jpg'] })} />
    </MemoryRouter>,
  )

  expect(screen.queryByRole('button', { name: /remove cover image/i })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: /replace cover image/i })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /remove gallery photo/i })).toBeInTheDocument()
})
