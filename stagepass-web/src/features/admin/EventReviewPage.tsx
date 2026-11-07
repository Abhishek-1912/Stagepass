import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/features/organizer/events/StatusBadge'
import type { EventStatus } from '@/lib/api/types'
import { formatEventDate } from '@/lib/datetime'
import { SearchBox } from '@/features/organizer/manage/ListControls'
import { isApiError } from '@/lib/api/ApiError'
import { cn } from '@/lib/utils'
import { ConfirmAction } from './ConfirmAction'
import { useAdminEventSearch, useCancelEvent } from './api'

const TABS: { status: EventStatus; label: string }[] = [
  { status: 'PENDING_REVIEW', label: 'To review' },
  { status: 'PUBLISHED', label: 'Live' },
  { status: 'REJECTED', label: 'Rejected' },
  { status: 'CANCELLED', label: 'Cancelled' },
]

export function EventReviewPage() {
  const [status, setStatus] = useState<EventStatus>('PENDING_REVIEW')
  const [q, setQ] = useState('')
  const { data, isPending } = useAdminEventSearch(status, q)
  const cancel = useCancelEvent()

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Event review</h2>
        <p className="text-muted-foreground">Oldest submissions first. Search by title or organizer.</p>
      </div>
      <SearchBox placeholder="Search events or organizers" onSearch={setQ} />
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
            <li key={e.id} className="flex items-center gap-2 pr-4">
              <Link to={`/admin/events/${e.id}`} className="flex min-w-0 flex-1 items-center gap-4 p-4 transition-colors hover:bg-muted/50">
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
              {(e.status === 'PUBLISHED' || e.status === 'PENDING_REVIEW') && (
                <ConfirmAction
                  trigger={
                    <Button size="sm" variant="outline" aria-label={`Cancel ${e.title}`}>
                      Cancel
                    </Button>
                  }
                  title={`Cancel ${e.title}?`}
                  effect="The event is cancelled for good. Every ticket holder gets a full refund, convenience fee included, and an email."
                  confirmLabel="Cancel event and refund"
                  requireReason
                  destructive
                  onConfirm={async (reason) => {
                    try {
                      await cancel.mutateAsync({ id: e.id, reason })
                      toast.success(`${e.title} cancelled`)
                    } catch (err) {
                      toast.error(isApiError(err) ? err.detail : 'Could not cancel')
                      throw err
                    }
                  }}
                />
              )}
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
