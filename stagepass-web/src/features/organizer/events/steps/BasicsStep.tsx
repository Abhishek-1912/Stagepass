import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { SelectField, TextAreaField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { istInputToIso } from '@/lib/datetime'
import { useCategories, useCreateEvent, useUpdateEvent } from '../../api'
import { basicsSchema, scheduleSchema } from '../eventSchema'

const newEventSchema = basicsSchema.and(scheduleSchema)
type FormValues = { title: string; categoryId: string; summary?: string; description?: string; terms?: string; startsAt?: string; endsAt?: string }

/** Step 1. For a new event it also asks for start/end, which the API needs to create the draft. */
export function BasicsStep({ event }: { event?: EventDetail }) {
  const navigate = useNavigate()
  const { data: categories } = useCategories()
  const create = useCreateEvent()
  const update = useUpdateEvent(event?.id ?? '')
  const isNew = !event
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(isNew ? newEventSchema : basicsSchema) as never,
    defaultValues: {
      title: event?.title ?? '',
      categoryId: event?.categoryId ?? '',
      summary: event?.summary ?? '',
      description: event?.description ?? '',
      terms: event?.terms ?? '',
    },
  })

  async function onSubmit(v: FormValues) {
    const input = {
      title: v.title,
      categoryId: v.categoryId,
      summary: v.summary || undefined,
      description: v.description || undefined,
      terms: v.terms || undefined,
    }
    try {
      if (isNew) {
        const created = await create.mutateAsync({
          ...input,
          startsAt: istInputToIso(v.startsAt!),
          endsAt: istInputToIso(v.endsAt!),
        })
        navigate(`/organizer/events/${created.id}/when-where`, { replace: true })
      } else {
        const clear = (['summary', 'description', 'terms'] as const).filter((f) => !v[f]?.trim() && event[f])
        await update.mutateAsync(clear.length ? { ...input, clear: [...clear] } : input)
        navigate(`/organizer/events/${event.id}/when-where`)
      }
    } catch (e) {
      if (isApiError(e) && e.fieldErrors.length) {
        e.fieldErrors.forEach((f) => setError(f.field as keyof FormValues, { message: f.message }))
      } else {
        toast.error(isApiError(e) ? e.detail : 'Could not save the event')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="max-w-3xl space-y-5">
      <TextField label="Event title" placeholder="Sunburn Goa 2026" error={errors.title?.message} {...register('title')} />
      <SelectField label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
        <option value="">Choose a category</option>
        {categories?.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </SelectField>
      <TextField
        label="Short summary"
        placeholder="One line that sells the event"
        hint="Shown on event cards. Up to 280 characters."
        error={errors.summary?.message}
        {...register('summary')}
      />
      <TextAreaField label="Description" className="min-h-48" hint="Line-up, schedule, what’s included." error={errors.description?.message} {...register('description')} />
      {isNew && (
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts (IST)" type="datetime-local" error={errors.startsAt?.message} {...register('startsAt')} />
          <TextField label="Ends (IST)" type="datetime-local" error={errors.endsAt?.message} {...register('endsAt')} />
        </div>
      )}
      <TextAreaField label="Terms & conditions (optional)" hint="Age limits, entry rules, prohibited items." {...register('terms')} />
      <div className="flex justify-end">
        <Button type="submit" size="lg" className="rounded-full px-6" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : isNew ? 'Create draft' : 'Save and continue'}
        </Button>
      </div>
    </form>
  )
}
