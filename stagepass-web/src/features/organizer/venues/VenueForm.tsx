import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { SelectField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { INDIAN_STATES } from '@/features/organizer/applicationSchema'
import { isApiError } from '@/lib/api/ApiError'
import type { Venue } from '@/lib/api/types'
import { mapsKey } from '@/lib/maps'
import { useSaveVenue } from '../api'
import { type PickedPlace, PlacesAutocomplete } from './PlacesAutocomplete'

const optionalNumber = (min: number, max: number) =>
  z
    .string()
    .trim()
    .transform((v) => (v === '' ? undefined : Number(v)))
    .pipe(z.number().min(min).max(max).optional())

const venueSchema = z.object({
  name: z.string().trim().min(1, 'Name the venue').max(120),
  addressLine: z.string().trim().min(1, 'Add the street address').max(300),
  city: z.string().trim().min(1, 'Add the city').max(80),
  state: z.string().optional(),
  pincode: z
    .string()
    .trim()
    .transform((v) => (v === '' ? undefined : v))
    .pipe(z.string().regex(/^[1-9][0-9]{5}$/, 'Enter a 6-digit PIN code').optional()),
  lat: optionalNumber(-90, 90),
  lng: optionalNumber(-180, 180),
  googlePlaceId: z.string().optional(),
})
type VenueFormInput = z.input<typeof venueSchema>
type VenueFormValues = z.output<typeof venueSchema>

export function VenueForm({ venue, onSaved }: { venue?: Venue; onSaved: (v: Venue) => void }) {
  const save = useSaveVenue()
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<VenueFormInput, unknown, VenueFormValues>({
    resolver: zodResolver(venueSchema),
    defaultValues: {
      name: venue?.name ?? '',
      addressLine: venue?.addressLine ?? '',
      city: venue?.city ?? '',
      state: venue?.state ?? '',
      pincode: venue?.pincode ?? '',
      lat: venue?.lat?.toString() ?? '',
      lng: venue?.lng?.toString() ?? '',
      googlePlaceId: venue?.googlePlaceId ?? undefined,
    },
  })

  function fillFrom(place: PickedPlace) {
    const opts = { shouldValidate: true, shouldDirty: true } as const
    setValue('name', place.name, opts)
    setValue('addressLine', place.addressLine, opts)
    setValue('city', place.city, opts)
    setValue('state', place.state ?? '', opts)
    setValue('pincode', place.pincode ?? '', opts)
    setValue('lat', place.lat?.toString() ?? '', opts)
    setValue('lng', place.lng?.toString() ?? '', opts)
    setValue('googlePlaceId', place.googlePlaceId, opts)
  }

  async function onSubmit(values: VenueFormValues) {
    try {
      const saved = await save.mutateAsync({ id: venue?.id, input: { ...values, state: values.state || undefined } })
      toast.success(venue ? 'Venue updated' : 'Venue added')
      onSaved(saved)
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save the venue')
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4">
      {mapsKey() && <PlacesAutocomplete onPlace={fillFrom} />}
      <TextField label="Venue name" error={errors.name?.message} {...register('name')} />
      <TextField label="Street address" error={errors.addressLine?.message} {...register('addressLine')} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="City" error={errors.city?.message} {...register('city')} />
        <TextField label="PIN code" inputMode="numeric" error={errors.pincode?.message} {...register('pincode')} />
      </div>
      <SelectField label="State" {...register('state')}>
        <option value="">Select state</option>
        {INDIAN_STATES.map((s) => (
          <option key={s}>{s}</option>
        ))}
      </SelectField>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Latitude (optional)" inputMode="decimal" error={errors.lat?.message} {...register('lat')} />
        <TextField label="Longitude (optional)" inputMode="decimal" error={errors.lng?.message} {...register('lng')} />
      </div>
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Saving…' : 'Save venue'}
      </Button>
    </form>
  )
}
