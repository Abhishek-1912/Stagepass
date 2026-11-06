import { MapPin, Video } from 'lucide-react'
import { Link } from 'react-router'
import type { PublicEventCard } from '@/features/discovery/types'
import { cloudinary } from '@/lib/images'
import { formatPaise } from '@/lib/money'
import { cn } from '@/lib/utils'

const day = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric' })
const month = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', month: 'short' })
const time = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  weekday: 'short',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

function PricePill({ event }: { event: PublicEventCard }) {
  if (event.soldOut) {
    return <span className="rounded-full bg-muted px-2.5 py-1 text-xs font-semibold text-muted-foreground">Sold out</span>
  }
  if (event.minPricePaise === null) return null
  return (
    <span className="rounded-full bg-accent px-2.5 py-1 text-xs font-semibold text-accent-foreground">
      {event.minPricePaise === 0 ? 'Free' : `From ${formatPaise(event.minPricePaise)}`}
    </span>
  )
}

export function EventCard({ event, variant = 'grid' }: { event: PublicEventCard; variant?: 'grid' | 'rail' | 'large' }) {
  const start = new Date(event.startsAt)
  const where = event.online ? 'Online' : [event.venueName, event.city].filter(Boolean).join(' · ')
  return (
    <Link
      to={`/events/${event.slug}`}
      className={cn(
        'group block overflow-hidden rounded-2xl border border-border bg-card shadow-[var(--shadow-card)] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[var(--shadow-card-hover)] focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
        variant === 'rail' && 'w-72 shrink-0 snap-start',
      )}
    >
      <div className={cn('relative overflow-hidden bg-muted', variant === 'large' ? 'aspect-[16/9]' : 'aspect-[3/2]')}>
        {event.coverImageUrl ? (
          <img
            src={cloudinary(event.coverImageUrl, variant === 'large' ? 'f_auto,q_auto,w_1200' : 'f_auto,q_auto,w_640')}
            alt=""
            loading="lazy"
            className="size-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
          />
        ) : (
          <div className="bg-gradient-brand size-full opacity-50" />
        )}
        <div className="absolute left-3 top-3 grid min-w-12 place-items-center rounded-xl bg-background/95 px-2 py-1.5 text-center shadow-sm backdrop-blur">
          <span className="font-display text-lg font-bold leading-none">{day.format(start)}</span>
          <span className="text-[10px] font-semibold uppercase tracking-wider text-primary">{month.format(start)}</span>
        </div>
        {event.featured && (
          <span className="bg-gradient-brand absolute right-3 top-3 rounded-full px-2.5 py-1 text-[11px] font-semibold text-white">
            Featured
          </span>
        )}
      </div>
      <div className="space-y-2 p-4">
        <p className="text-xs font-medium text-muted-foreground">
          {event.categoryName} · {time.format(start)}
        </p>
        <h3 className="line-clamp-2 font-sans text-base font-semibold leading-snug tracking-normal group-hover:text-primary">
          {event.title}
        </h3>
        <div className="flex items-center justify-between gap-2 pt-1">
          <span className="flex min-w-0 items-center gap-1 text-sm text-muted-foreground">
            {event.online ? <Video className="size-3.5 shrink-0" /> : <MapPin className="size-3.5 shrink-0" />}
            <span className="truncate">{where}</span>
            {event.distanceKm != null && <span className="shrink-0">· {event.distanceKm.toFixed(1)} km</span>}
          </span>
          <PricePill event={event} />
        </div>
      </div>
    </Link>
  )
}
