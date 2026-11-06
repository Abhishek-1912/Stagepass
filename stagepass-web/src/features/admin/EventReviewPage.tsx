import { useState } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { StatusBadge } from '@/features/organizer/events/StatusBadge'
import type { EventStatus } from '@/lib/api/types'
import { formatEventDate } from '@/lib/datetime'
import { cn } from '@/lib/utils'
import { useAdminEvents } from './api'

const TABS: { status: EventStatus; label: string }[] = [
  { status: 'PENDING_REVIEW', label: 'To review' },
  { status: 'PUBLISHED', label: 'Live' },
  { status: 'REJECTED', label: 'Rejected' },
  { status: 'CANCELLED', label: 'Cancelled' },
]

export function EventReviewPage() {
  const [status, setStatus] = useState<EventStatus>('PENDING_REVIEW')
  const { data, isPending } = useAdminEvents(status)

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Event review</h2>
        <p className="text-muted-foreground">Oldest submissions first.</p>
      </div>
      <div role="tablist" className="-mx-4 flex gap-1 overflow-x-auto px-4">
        {TABS.map((t) => (
          <button
            key={t.status}
            role="tab"
            aria-selected={status === t.status}
            onClick={() => setStatus(t.status)}
            className={cn(
              'whitespace-nowrap rounded-full px-3.5 py-1.5 text-sm font-medium',
              status === t.status ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      {isPending ? (
        <PageLoader />
      ) : data?.items.length ? (
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
          {data.items.map((e) => (
            <li key={e.id}>
              <Link to={`/admin/events/${e.id}`} className="flex items-center gap-4 p-4 transition-colors hover:bg-muted/50">
                {e.coverImageUrl ? (
                  <img src={e.coverImageUrl} alt="" className="h-14 w-24 shrink-0 rounded-lg object-cover" />
                ) : (
                  <span className="bg-gradient-brand h-14 w-24 shrink-0 rounded-lg opacity-40" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{e.title}</p>
                  <p className="truncate text-sm text-muted-foreground">
                    {e.organizerName} · {formatEventDate(e.startsAt)} · {e.online ? 'Online' : (e.city ?? '—')}
                  </p>
                </div>
                {e.featured && <span className="text-xs font-semibold text-primary">Featured</span>}
                <StatusBadge status={e.status} />
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
          Nothing to review. 🎉
        </div>
      )}
    </div>
  )
}
