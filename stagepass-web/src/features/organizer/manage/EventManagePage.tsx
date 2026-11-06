import { ArrowLeft, ExternalLink, Pencil } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { formatEventDate } from '@/lib/datetime'
import { cn } from '@/lib/utils'
import { useOrganizerEvent } from '../api'
import { StatusBadge } from '../events/StatusBadge'
import { AttendeesTab } from './AttendeesTab'
import { BookingsTab } from './BookingsTab'
import { CheckInTab } from './CheckInTab'
import { RefundsTab } from './RefundsTab'
import { SalesTab } from './SalesTab'

const TABS = [
  { key: 'sales', label: 'Sales' },
  { key: 'bookings', label: 'Bookings' },
  { key: 'attendees', label: 'Attendees' },
  { key: 'refunds', label: 'Refunds' },
  { key: 'checkin', label: 'Check-in' },
] as const
type TabKey = (typeof TABS)[number]['key']

/** `/organizer/events/:id/manage` — everything about a live or past event, one tab at a time (`?tab=`). */
export function EventManagePage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const event = useOrganizerEvent(id)
  const requested = params.get('tab')
  const tab: TabKey = TABS.some((t) => t.key === requested) ? (requested as TabKey) : 'sales'

  if (event.isPending) return <PageLoader label="Loading event" />
  if (!event.data) return <p className="py-16 text-center text-muted-foreground">We couldn’t find this event.</p>
  const e = event.data

  return (
    <div className="space-y-6">
      <Link to="/organizer/events" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden /> Events
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <StatusBadge status={e.status} />
          </div>
          <h2 className="text-2xl font-bold">{e.title}</h2>
          <p className="text-sm text-muted-foreground">{formatEventDate(e.startsAt)}</p>
        </div>
        <div className="flex gap-2">
          {e.status === 'PUBLISHED' && (
            <Button asChild variant="outline" className="rounded-full">
              <Link to={`/events/${e.slug}`} target="_blank" rel="noreferrer">
                <ExternalLink className="size-4" aria-hidden /> View page
              </Link>
            </Button>
          )}
          <Button asChild variant="outline" className="rounded-full">
            <Link to={`/organizer/events/${e.id}/review`}>
              <Pencil className="size-4" aria-hidden /> Edit event
            </Link>
          </Button>
        </div>
      </div>

      <div role="tablist" aria-label="Event sections" className="flex gap-1 overflow-x-auto border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setParams(t.key === 'sales' ? {} : { tab: t.key }, { replace: true })}
            className={cn(
              '-mb-px whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium transition',
              tab === t.key ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div role="tabpanel">
        {tab === 'sales' && <SalesTab eventId={e.id} />}
        {tab === 'bookings' && <BookingsTab eventId={e.id} />}
        {tab === 'attendees' && <AttendeesTab eventId={e.id} />}
        {tab === 'refunds' && <RefundsTab eventId={e.id} />}
        {tab === 'checkin' && <CheckInTab eventId={e.id} />}
      </div>
    </div>
  )
}
