import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'

const maps = vi.hoisted(() => ({ mapsKey: vi.fn() }))
vi.mock('@/lib/maps', () => maps)
const save = vi.hoisted(() => vi.fn())
vi.mock('../api', () => ({ useSaveVenue: () => ({ mutateAsync: save, isPending: false }) }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('./PlacesAutocomplete', () => ({
  PlacesAutocomplete: ({ onPlace }: { onPlace: (p: object) => void }) => (
    <button
      type="button"
      onClick={() =>
        onPlace({
          name: 'NSCI Dome',
          addressLine: 'Lala Lajpat Rai Marg, Worli',
          city: 'Mumbai',
          state: 'Maharashtra',
          pincode: '400018',
          lat: 19.0176,
          lng: 72.8166,
          googlePlaceId: 'ChIJ123',
        })
      }
    >
      Pick NSCI Dome
    </button>
  ),
}))

import { VenueForm } from './VenueForm'

beforeEach(() => {
  save.mockReset().mockResolvedValue({ id: 'v1' })
})

test('choosing a place fills the venue fields', async () => {
  maps.mapsKey.mockReturnValue('browser-key')
  render(<VenueForm onSaved={vi.fn()} />)

  await userEvent.click(screen.getByRole('button', { name: /pick nsci dome/i }))

  expect(screen.getByLabelText('Venue name')).toHaveValue('NSCI Dome')
  expect(screen.getByLabelText('City')).toHaveValue('Mumbai')
  expect(screen.getByLabelText('PIN code')).toHaveValue('400018')
  expect(screen.getByLabelText('State')).toHaveValue('Maharashtra')

  await userEvent.click(screen.getByRole('button', { name: /save venue/i }))
  expect(save).toHaveBeenCalledWith({
    id: undefined,
    input: expect.objectContaining({ name: 'NSCI Dome', lat: 19.0176, lng: 72.8166, googlePlaceId: 'ChIJ123' }),
  })
})

test('venue form works without a maps key', () => {
  maps.mapsKey.mockReturnValue(undefined)
  render(<VenueForm onSaved={vi.fn()} />)

  expect(screen.queryByRole('button', { name: /pick nsci dome/i })).not.toBeInTheDocument()
  expect(screen.getByLabelText('Venue name')).toBeInTheDocument()
  expect(screen.getByLabelText('Latitude (optional)')).toBeInTheDocument()
})
