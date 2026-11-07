import { CalendarDays, MapPin, ScanLine } from 'lucide-react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { formatEventDate } from '@/lib/datetime'
import { useScannerEvents } from './api'

/** `/scan` — events the signed-in user can check guests in for. */
export function ScannerHomePage() {
  const events = useScannerEvents()

  return (
    <div className="mx-auto max-w-lg space-y-6 px-4 py-8">
      <div className="flex items-center gap-3">
        <span className="grid size-10 place-items-center rounded-2xl bg-primary text-primary-foreground">
          <ScanLine className="size-5" aria-hidden />
        </span>
        <div>
          <h1 className="text-2xl font-bold">Door scanner</h1>
          <p className="text-sm text-muted-foreground">Pick the event you’re checking guests in for.</p>
        </div>
      </div>

      {events.isPending ? (
        <PageLoader label="Loading your events" />
      ) : !events.data || events.data.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          You’re not on the door team for any upcoming event. Ask the organizer to add your email in their Door staff page.
        </p>
      ) : (
        <ul className="space-y-3">
          {events.data.map((e) => (
            <li key={e.id}>
              <Link to={`/scan/${e.id}`} className="block space-y-1 rounded-2xl border border-border bg-card p-4 shadow-[var(--shadow-card)] hover:border-primary/50">
                <p className="text-lg font-semibold">{e.title}</p>
                <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                  <CalendarDays className="size-4" aria-hidden /> {formatEventDate(e.startsAt)}
                </p>
                <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                  <MapPin className="size-4" aria-hidden /> {e.venue}
                </p>
                <p className="text-xs font-medium text-primary">{e.owner ? 'Your event' : `Door staff · ${e.organizerName}`}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
