import { CalendarDays, Ticket } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import { formatEventDate } from '@/lib/datetime'
import { cloudinary, placeholderImages } from '@/lib/images'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { type BookingScope, type BookingSummary, useMyBookings } from './api'
import { BookingStatusBadge } from './BookingStatusBadge'

function BookingCard({ b }: { b: BookingSummary }) {
  return (
    <Link
      to={`/account/bookings/${b.id}`}
      className="flex gap-4 rounded-2xl border border-border bg-card p-3 shadow-[var(--shadow-card)] transition hover:border-primary/40"
    >
      <img
        src={cloudinary(b.coverImageUrl, 'f_auto,q_auto,c_fill,w_240,h_240') ?? placeholderImages.stageLights}
        alt=""
        className="size-20 shrink-0 rounded-xl object-cover sm:size-24"
        loading="lazy"
      />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex items-start justify-between gap-2">
          <h2 className="truncate font-sans text-base font-semibold tracking-normal">{b.eventTitle}</h2>
          <BookingStatusBadge status={b.status} className="shrink-0" />
        </div>
        <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <CalendarDays className="size-4" aria-hidden /> {formatEventDate(b.eventStartsAt)}
          {b.city ? ` · ${b.city}` : ''}
        </p>
        <p className="text-sm text-muted-foreground">
          {b.code} · {b.ticketCount} {b.ticketCount === 1 ? 'ticket' : 'tickets'} ·{' '}
          <span className="text-foreground">{b.totalPaise === 0 ? 'Free' : formatPaiseExact(b.totalPaise)}</span>
        </p>
      </div>
    </Link>
  )
}

/** `/account/bookings` — the attendee's tickets, upcoming first. */
export function MyBookingsPage() {
  const [params, setParams] = useSearchParams()
  const scope: BookingScope = params.get('scope') === 'past' ? 'past' : 'upcoming'
  const bookings = useMyBookings(scope)

  const tab = (s: BookingScope, label: string) => (
    <button
      type="button"
      role="tab"
      aria-selected={scope === s}
      onClick={() => setParams(s === 'upcoming' ? {} : { scope: s }, { replace: true })}
      className={cn(
        'rounded-full px-4 py-1.5 text-sm font-medium transition',
        scope === s ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
      )}
    >
      {label}
    </button>
  )

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
      <h1 className="text-3xl font-bold">My tickets</h1>
      <div role="tablist" aria-label="Bookings" className="inline-flex gap-1 rounded-full border border-border p-1">
        {tab('upcoming', 'Upcoming')}
        {tab('past', 'Past')}
      </div>

      {bookings.isPending ? (
        <PageLoader label="Loading your bookings" />
      ) : bookings.error ? (
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">
          {isApiError(bookings.error) ? bookings.error.detail : 'We couldn’t load your bookings.'}
        </p>
      ) : bookings.data.items.length === 0 ? (
        <div className="space-y-4 rounded-3xl border border-dashed border-border p-10 text-center">
          <Ticket className="mx-auto size-8 text-muted-foreground" aria-hidden />
          <p className="text-muted-foreground">
            {scope === 'upcoming' ? 'No upcoming events yet. Find something worth going to.' : 'No past events yet.'}
          </p>
          <Button asChild className="rounded-full">
            <Link to="/explore">Explore events</Link>
          </Button>
        </div>
      ) : (
        <ul className="space-y-3">
          {bookings.data.items.map((b) => (
            <li key={b.id}>
              <BookingCard b={b} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
