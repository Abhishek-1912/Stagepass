import { ArrowLeft, Check } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { cn } from '@/lib/utils'
import { useOrganizerEvent } from '../api'
import { StatusBadge } from './StatusBadge'
import { BasicsStep } from './steps/BasicsStep'
import { MediaStep } from './steps/MediaStep'
import { ReviewStep } from './steps/ReviewStep'
import { TicketsStep } from './steps/TicketsStep'
import { WhenWhereStep } from './steps/WhenWhereStep'

const STEPS = [
  { key: 'basics', label: 'Basics', done: (e?: EventDetail) => !!e?.summary },
  { key: 'when-where', label: 'Date & venue', done: (e?: EventDetail) => !!e && (e.online ? !!e.onlineUrl : !!e.venue) },
  { key: 'tickets', label: 'Tickets', done: (e?: EventDetail) => !!e?.ticketTypes.length },
  { key: 'media', label: 'Images', done: (e?: EventDetail) => !!e?.coverImageUrl },
  { key: 'review', label: 'Review', done: (e?: EventDetail) => !!e && e.status !== 'DRAFT' && e.status !== 'REJECTED' },
] as const
type StepKey = (typeof STEPS)[number]['key']

function Stepper({ event, current }: { event?: EventDetail; current: StepKey }) {
  return (
    <ol className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
      {STEPS.map((s, i) => {
        const active = s.key === current
        const done = s.done(event)
        const enabled = !!event || s.key === 'basics'
        const content = (
          <span
            className={cn(
              'flex items-center gap-2 whitespace-nowrap rounded-full border px-3 py-1.5 text-sm transition-colors',
              active ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-card text-muted-foreground',
              enabled && !active && 'hover:text-foreground',
            )}
          >
            <span
              className={cn(
                'grid size-5 place-items-center rounded-full text-[11px] font-semibold',
                active ? 'bg-white/20' : done ? 'bg-success text-white' : 'bg-muted',
              )}
            >
              {done && !active ? <Check className="size-3" /> : i + 1}
            </span>
            {s.label}
          </span>
        )
        return (
          <li key={s.key}>
            {enabled && event ? (
              <Link to={`/organizer/events/${event.id}/${s.key}`} aria-current={active ? 'step' : undefined}>
                {content}
              </Link>
            ) : (
              content
            )}
          </li>
        )
      })}
    </ol>
  )
}

export function EventEditorPage() {
  const { id, step } = useParams()
  const isNew = !id
  const current = (STEPS.find((s) => s.key === step)?.key ?? 'basics') as StepKey
  const { data: event, isPending, error } = useOrganizerEvent(id)

  if (!isNew && isPending) return <PageLoader />
  if (!isNew && isApiError(error) && error.status === 404) return <Navigate to="/organizer/events" replace />
  if (!isNew && !step) return <Navigate to={`/organizer/events/${id}/basics`} replace />

  const readOnly = event && (event.status === 'CANCELLED' || event.status === 'COMPLETED')

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <Link to="/organizer/events" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" /> All events
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-2xl font-bold">{event?.title ?? 'New event'}</h2>
          {event && <StatusBadge status={event.status} />}
        </div>
        <Stepper event={event} current={current} />
      </div>

      {readOnly ? (
        <div className="rounded-2xl border border-border bg-card p-6 text-muted-foreground">
          This event is {event.status.toLowerCase()} and can no longer be edited.
        </div>
      ) : current === 'basics' || isNew ? (
        <BasicsStep key={event?.id ?? 'new'} event={event} />
      ) : event ? (
        {
          'when-where': <WhenWhereStep key={event.id} event={event} />,
          tickets: <TicketsStep event={event} />,
          media: <MediaStep event={event} />,
          review: <ReviewStep event={event} />,
        }[current as Exclude<StepKey, 'basics'>]
      ) : null}
    </div>
  )
}
