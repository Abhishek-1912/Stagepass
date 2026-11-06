import { CalendarPlus, Ticket } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import type { EventStatus, EventSummary } from '@/lib/api/types'
import { formatEventDate } from '@/lib/datetime'
import { formatPaise } from '@/lib/money'
import { cn } from '@/lib/utils'
import { useOrganizerEvents } from '../api'
import { StatusBadge } from './StatusBadge'

const TABS: { key: string; label: string; statuses?: EventStatus[] }[] = [
  { key: 'all', label: 'All' },
  { key: 'drafts', label: 'Drafts', statuses: ['DRAFT', 'REJECTED'] },
  { key: 'review', label: 'In review', statuses: ['PENDING_REVIEW'] },
  { key: 'live', label: 'Live', statuses: ['PUBLISHED'] },
  { key: 'past', label: 'Past & cancelled', statuses: ['COMPLETED', 'CANCELLED'] },
]

function EventCard({ e }: { e: EventSummary }) {
  const nextStep = e.status === 'DRAFT' || e.status === 'REJECTED' ? 'basics' : 'review'
  return (
    <Link
      to={`/organizer/events/${e.id}/${nextStep}`}
      className="group overflow-hidden rounded-2xl border border-border bg-card shadow-[var(--shadow-card)] transition-all hover:-translate-y-0.5 hover:shadow-[var(--shadow-card-hover)]"
    >
      <div className="relative aspect-[16/9] bg-muted">
        {e.coverImageUrl ? (
          <img src={e.coverImageUrl} alt="" loading="lazy" className="size-full object-cover" />
        ) : (
          <div className="bg-gradient-brand size-full opacity-40" />
        )}
        <StatusBadge status={e.status} className="absolute left-3 top-3 bg-background/90 backdrop-blur" />
      </div>
      <div className="space-y-1.5 p-4">
        <p className="line-clamp-1 font-semibold group-hover:text-primary">{e.title}</p>
        <p className="text-sm text-muted-foreground">
          {formatEventDate(e.startsAt)}
          {e.city ? ` · ${e.city}` : ''}
        </p>
        <div className="flex items-center justify-between pt-1 text-sm">
          <span>{e.minPricePaise === null ? 'No tickets yet' : `From ${formatPaise(e.minPricePaise)}`}</span>
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <Ticket className="size-3.5" /> {e.sold}/{e.totalCapacity}
          </span>
        </div>
      </div>
    </Link>
  )
}

export function EventsListPage() {
  const { data, isPending } = useOrganizerEvents()
  const [tab, setTab] = useState('all')
  const statuses = TABS.find((t) => t.key === tab)?.statuses
  const events = (data?.items ?? []).filter((e) => !statuses || statuses.includes(e.status))

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-bold">Events</h2>
        <Button asChild className="rounded-full">
          <Link to="/organizer/events/new">
            <CalendarPlus /> Create event
          </Link>
        </Button>
      </div>
      <div role="tablist" className="-mx-4 flex gap-1 overflow-x-auto px-4">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'whitespace-nowrap rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors',
              tab === t.key ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      {isPending ? (
        <PageLoader />
      ) : events.length ? (
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {events.map((e) => (
            <EventCard key={e.id} e={e} />
          ))}
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-border p-12 text-center">
          <p className="font-medium">No events here yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Create your first event — it stays a private draft until you submit it.</p>
        </div>
      )}
    </div>
  )
}
