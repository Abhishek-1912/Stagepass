import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { SelectField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { istInputToIso, isoToIstInput } from '@/lib/datetime'
import { useUpdateEvent, useVenues } from '../../api'
import { VenueForm } from '../../venues/VenueForm'
import { type WhenWhereValues, whenWhereSchema } from '../eventSchema'

export function WhenWhereStep({ event }: { event: EventDetail }) {
  const navigate = useNavigate()
  const update = useUpdateEvent(event.id)
  const { data: venues } = useVenues()
  const [venueSheet, setVenueSheet] = useState(false)
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<WhenWhereValues>({
    resolver: zodResolver(whenWhereSchema),
    defaultValues: {
      startsAt: isoToIstInput(event.startsAt),
      endsAt: isoToIstInput(event.endsAt),
      online: event.online,
      venueId: event.venue?.id ?? '',
      onlineUrl: event.onlineUrl ?? '',
    },
  })
  const online = useWatch({ control, name: 'online' })

  async function onSubmit(v: WhenWhereValues) {
    try {
      await update.mutateAsync({
        startsAt: istInputToIso(v.startsAt),
        endsAt: istInputToIso(v.endsAt),
        online: v.online,
        ...(v.online ? { onlineUrl: v.onlineUrl } : { venueId: v.venueId }),
      })
      navigate(`/organizer/events/${event.id}/tickets`)
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save')
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="max-w-3xl space-y-6">
      {event.status === 'PUBLISHED' && (
        <p role="status" className="rounded-2xl border border-warning/40 bg-warning/10 p-4 text-sm">
          This event is live. Changing the date, time or venue emails every ticket holder and lets them get a full refund
          for 7 days.
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Starts (IST)" type="datetime-local" error={errors.startsAt?.message} {...register('startsAt')} />
        <TextField label="Ends (IST)" type="datetime-local" error={errors.endsAt?.message} {...register('endsAt')} />
      </div>

      <label className="flex items-center gap-3 rounded-2xl border border-border bg-card p-4 text-sm">
        <input type="checkbox" className="size-4 accent-[var(--primary)]" {...register('online')} />
        <span>
          <span className="font-medium">This is an online event</span>
          <span className="block text-muted-foreground">Attendees get the link with their ticket.</span>
        </span>
      </label>

      {online ? (
        <TextField label="Online event link" type="url" placeholder="https://" error={errors.onlineUrl?.message} {...register('onlineUrl')} />
      ) : (
        <div className="flex items-end gap-3">
          <div className="flex-1">
            <SelectField label="Venue" error={errors.venueId?.message} {...register('venueId')}>
              <option value="">Choose a venue</option>
              {venues?.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} — {v.city}
                </option>
              ))}
            </SelectField>
          </div>
          <Button type="button" variant="outline" className="h-11" onClick={() => setVenueSheet(true)}>
            <Plus /> New venue
          </Button>
        </div>
      )}

      <div className="flex justify-between">
        <Button type="button" variant="ghost" onClick={() => navigate(`/organizer/events/${event.id}/basics`)}>
          Back
        </Button>
        <Button type="submit" size="lg" className="rounded-full px-6" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : 'Save and continue'}
        </Button>
      </div>

      <Sheet open={venueSheet} onOpenChange={setVenueSheet}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          <SheetHeader>
            <SheetTitle>New venue</SheetTitle>
          </SheetHeader>
          <div className="px-4 pb-6">
            <VenueForm
              onSaved={(v) => {
                setVenueSheet(false)
                setValue('venueId', v.id, { shouldValidate: true })
              }}
            />
          </div>
        </SheetContent>
      </Sheet>
    </form>
  )
}
