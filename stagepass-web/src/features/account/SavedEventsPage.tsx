import { Heart } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { EventCard } from '@/components/events/EventCard'
import { Button } from '@/components/ui/button'
import { Pager } from '@/features/organizer/manage/ListControls'
import { useSavedEvents } from './api'

function statusLabel(status: string, endsAt: string): string | null {
  if (status === 'CANCELLED') return 'Cancelled'
  if (status === 'COMPLETED' || new Date(endsAt).getTime() < Date.now()) return 'Ended'
  return null
}

/** Events the user hearted, newest save first. */
export function SavedEventsPage() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useSavedEvents(page)

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-10 sm:px-6">
      <div>
        <h1 className="text-3xl font-bold">Saved events</h1>
        <p className="text-muted-foreground">Tap the heart on any event to keep it here.</p>
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">We couldn’t load your saved events.</p>
      ) : !data?.items.length ? (
        <div className="space-y-4 rounded-2xl border border-dashed border-border p-10 text-center">
          <Heart className="mx-auto size-8 text-muted-foreground" aria-hidden />
          <p className="text-muted-foreground">Nothing saved yet.</p>
          <Button asChild>
            <Link to="/explore">Explore events</Link>
          </Button>
        </div>
      ) : (
        <>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {data.items.map((s) => {
              const label = statusLabel(s.status, s.event.endsAt)
              return (
                <div key={s.event.id} className="relative">
                  <EventCard event={s.event} />
                  {label && (
                    <span className="absolute left-3 top-16 rounded-full bg-foreground/85 px-2.5 py-1 text-xs font-semibold text-background">
                      {label}
                    </span>
                  )}
                </div>
              )
            })}
          </div>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}
