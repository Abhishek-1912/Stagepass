import { CheckCircle2, Circle, Clock, ExternalLink } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import type { EventDetail } from '@/lib/api/types'
import { formatEventDate } from '@/lib/datetime'
import { formatPaise } from '@/lib/money'
import { useSubmitEvent } from '../../api'
import { StatusBadge } from '../StatusBadge'
import { CancelEventButton } from './CancelEventButton'

type CheckKey = 'summary' | 'cover' | 'tickets' | 'future' | 'place'

const SERVER_FIELD_TO_CHECK: Record<string, CheckKey> = {
  summary: 'summary',
  coverImageUrl: 'cover',
  ticketTypes: 'tickets',
  startsAt: 'future',
  venueId: 'place',
  onlineUrl: 'place',
}

function checks(e: EventDetail): { key: CheckKey; label: string; done: boolean }[] {
  return [
    { key: 'summary', label: 'Add a short summary', done: !!e.summary?.trim() },
    { key: 'cover', label: 'Add a cover image', done: !!e.coverImageUrl },
    { key: 'tickets', label: 'Add at least one ticket type', done: e.ticketTypes.length > 0 },
    { key: 'future', label: 'Start time is in the future', done: new Date(e.startsAt) > new Date() },
    { key: 'place', label: 'Choose a venue or online link', done: e.online ? !!e.onlineUrl : !!e.venue },
  ]
}

export function ReviewStep({ event }: { event: EventDetail }) {
  const submit = useSubmitEvent(event.id)
  const [serverMissing, setServerMissing] = useState<Set<CheckKey>>(new Set())
  const items = checks(event).map((c) => ({ ...c, done: c.done && !serverMissing.has(c.key) }))
  const ready = items.every((c) => c.done)
  const canSubmit = event.status === 'DRAFT' || event.status === 'REJECTED'

  async function onSubmit() {
    setServerMissing(new Set())
    try {
      await submit.mutateAsync(undefined)
      toast.success('Submitted! We’ll email you once it’s reviewed.')
    } catch (e) {
      if (isApiError(e) && e.fieldErrors.length) {
        setServerMissing(new Set(e.fieldErrors.map((f) => SERVER_FIELD_TO_CHECK[f.field]).filter(Boolean) as CheckKey[]))
      } else {
        toast.error(isApiError(e) ? e.detail : 'Could not submit the event')
      }
    }
  }

  const minPrice = event.ticketTypes.length ? Math.min(...event.ticketTypes.map((t) => t.pricePaise)) : null

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
      <article className="overflow-hidden rounded-3xl border border-border bg-card shadow-[var(--shadow-card)]">
        {event.coverImageUrl ? (
          <img src={event.coverImageUrl} alt="" className="aspect-[16/9] w-full object-cover" />
        ) : (
          <div className="bg-gradient-brand aspect-[16/9] w-full opacity-60" />
        )}
        <div className="space-y-3 p-6">
          <div className="flex items-center gap-2">
            <StatusBadge status={event.status} />
            <span className="text-xs text-muted-foreground">{event.categoryName}</span>
          </div>
          <h2 className="text-2xl font-bold">{event.title}</h2>
          <p className="text-sm text-muted-foreground">
            {formatEventDate(event.startsAt)} · {event.online ? 'Online' : (event.venue ? `${event.venue.name}, ${event.venue.city}` : 'Venue to be added')}
          </p>
          {event.summary && <p>{event.summary}</p>}
          {minPrice !== null && <p className="font-semibold">From {formatPaise(minPrice)}</p>}
        </div>
      </article>

      <aside className="space-y-5">
        {event.status === 'REJECTED' && event.rejectionReason && (
          <div role="status" className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm">
            <p className="font-medium text-destructive">Changes requested by our team</p>
            <p className="mt-1">{event.rejectionReason}</p>
          </div>
        )}

        {event.status === 'PENDING_REVIEW' && (
          <div role="status" className="flex gap-3 rounded-2xl border border-border bg-card p-4 text-sm">
            <Clock className="size-5 shrink-0 text-warning" />
            <p>Waiting for review. Editing the event moves it back to draft, so you’ll need to resubmit.</p>
          </div>
        )}

        {event.status === 'PUBLISHED' && (
          <div className="space-y-3 rounded-2xl border border-border bg-card p-4 text-sm">
            <p className="font-medium text-success">Your event is live.</p>
            <Button asChild variant="outline" className="w-full">
              <Link to={`/events/${event.slug}`}>
                <ExternalLink /> View event page
              </Link>
            </Button>
          </div>
        )}

        {canSubmit && (
          <div className="space-y-4 rounded-2xl border border-border bg-card p-5">
            <h3 className="font-sans text-base font-semibold tracking-normal">Ready to publish?</h3>
            <ul className="space-y-2.5">
              {items.map((c) => (
                <li key={c.key} aria-label={c.label} data-done={String(c.done)} className="flex items-center gap-2 text-sm">
                  {c.done ? <CheckCircle2 className="size-4 text-success" /> : <Circle className="size-4 text-muted-foreground" />}
                  <span className={c.done ? '' : 'text-muted-foreground'}>{c.label}</span>
                </li>
              ))}
            </ul>
            <Button className="w-full rounded-full" disabled={!ready || submit.isPending} onClick={onSubmit}>
              {event.status === 'REJECTED' ? 'Resubmit for review' : 'Submit for review'}
            </Button>
            <p className="text-xs text-muted-foreground">Our team reviews events within 1 business day.</p>
          </div>
        )}

        {(event.status === 'PUBLISHED' || event.status === 'PENDING_REVIEW') && <CancelEventButton event={event} />}
      </aside>
    </div>
  )
}
