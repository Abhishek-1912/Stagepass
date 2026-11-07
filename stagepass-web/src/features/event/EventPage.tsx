import { CalendarPlus, CalendarX, Clock, MapPin, Video } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { usePublicEvent } from '@/features/discovery/api'
import type { PublicEventDetail } from '@/features/discovery/types'
import { isApiError } from '@/lib/api/ApiError'
import { formatEventDate } from '@/lib/datetime'
import { env } from '@/lib/env'
import { cloudinary } from '@/lib/images'
import { formatPaise } from '@/lib/money'
import { downloadIcs } from './calendar'
import { ReportEventLink } from './ReportEventLink'
import { ShareButton } from './ShareButton'
import { TicketPicker } from '@/features/checkout/TicketPicker'
import { bookLabel, canBook } from './bookLabel'
import { VenueMap } from './VenueMap'

function refundText(e: PublicEventDetail): string {
  if (e.refundPolicy === 'UNTIL_HOURS_BEFORE' && e.refundCutoffHours != null) {
    return `Refunds are available until ${e.refundCutoffHours} hours before the event starts. The convenience fee isn’t refunded.`
  }
  return 'Tickets are non-refundable, unless the event is cancelled or its date or venue changes.'
}

function timeRange(e: PublicEventDetail): string {
  const end = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit', hour12: true })
  return `${formatEventDate(e.startsAt)} – ${end.format(new Date(e.endsAt)).toUpperCase()}`
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-xl font-bold">{title}</h2>
      {children}
    </section>
  )
}

export function EventPage() {
  const { slug } = useParams()
  const { data: event, isPending, error } = usePublicEvent(slug)
  const [photo, setPhoto] = useState<string | null>(null)

  useEffect(() => {
    if (event) document.title = `${event.title} · Stagepass`
  }, [event])

  if (isPending) return <PageLoader />
  if (!event) {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-24 text-center">
        <CalendarX className="mx-auto size-10 text-muted-foreground" />
        <h1 className="text-2xl font-bold">This event isn’t available</h1>
        <p className="text-muted-foreground">
          {isApiError(error) && error.status !== 404 ? 'Please try again in a moment.' : 'It may have been removed or the link is wrong.'}
        </p>
        <Button asChild className="rounded-full">
          <Link to="/explore">Explore events</Link>
        </Button>
      </div>
    )
  }

  const shareUrl = `${env.apiBaseUrl}/e/${event.slug}`
  const where = event.online ? 'Online event' : event.venue ? `${event.venue.name}, ${event.venue.city}` : ''
  const fromPrice = event.minPricePaise === null ? null : event.minPricePaise === 0 ? 'Free' : `From ${formatPaise(event.minPricePaise)}`

  return (
    <article className="pb-[calc(5rem+env(safe-area-inset-bottom))] lg:pb-0">
      <header className="relative isolate overflow-hidden">
        {event.coverImageUrl && (
          <img src={cloudinary(event.coverImageUrl, 'f_auto,q_auto,w_1600')} alt="" className="absolute inset-0 -z-20 size-full object-cover" />
        )}
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(to_bottom,rgb(11_12_16/0.2),rgb(11_12_16/0.9))]" />
        <div className="mx-auto flex min-h-[420px] max-w-7xl flex-col justify-end gap-4 px-4 pb-10 pt-24 text-white sm:px-6 md:min-h-[520px]">
          <Link to={`/explore?category=${event.categorySlug}`} className="w-fit rounded-full bg-white/15 px-3 py-1 text-xs font-medium backdrop-blur hover:bg-white/25">
            {event.categoryName}
          </Link>
          <h1 className="max-w-4xl text-4xl font-bold leading-tight sm:text-5xl md:text-6xl">{event.title}</h1>
          <div className="flex flex-wrap gap-x-6 gap-y-2 text-white/85">
            <span className="inline-flex items-center gap-2"><Clock className="size-4" /> {timeRange(event)}</span>
            {where && (
              <span className="inline-flex items-center gap-2">
                {event.online ? <Video className="size-4" /> : <MapPin className="size-4" />} {where}
              </span>
            )}
          </div>
          <div className="flex flex-wrap gap-2 pt-2">
            <ShareButton url={shareUrl} title={event.title} className="rounded-full border-white/30 bg-white/10 text-white hover:bg-white/20" />
            {event.status === 'PUBLISHED' && (
              <Button
                variant="outline"
                className="rounded-full border-white/30 bg-white/10 text-white hover:bg-white/20"
                onClick={() =>
                  downloadIcs({
                    slug: event.slug,
                    title: event.title,
                    startsAt: event.startsAt,
                    endsAt: event.endsAt,
                    location: where,
                    url: `${window.location.origin}/events/${event.slug}`,
                    description: event.summary ?? undefined,
                  })
                }
              >
                <CalendarPlus /> Add to calendar
              </Button>
            )}
          </div>
        </div>
      </header>

      {event.status !== 'PUBLISHED' && (
        <div role="alert" className="border-b border-destructive/30 bg-destructive/10 px-4 py-3 text-center text-sm font-medium text-destructive">
          {event.status === 'CANCELLED'
            ? 'This event has been cancelled. Ticket holders are refunded automatically.'
            : 'This event has ended.'}
        </div>
      )}

      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-10 sm:px-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-10">
          {event.summary && <p className="text-lg">{event.summary}</p>}
          {event.description && (
            <Section title="About">
              <p className="whitespace-pre-line leading-relaxed text-muted-foreground">{event.description}</p>
            </Section>
          )}
          {event.gallery.length > 0 && (
            <Section title="Gallery">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {event.gallery.map((g) => (
                  <button key={g} type="button" onClick={() => setPhoto(g)} className="overflow-hidden rounded-xl">
                    <img src={cloudinary(g, 'f_auto,q_auto,w_600')} alt="Event photo" loading="lazy" className="aspect-square w-full object-cover transition-transform hover:scale-105" />
                  </button>
                ))}
              </div>
              <Dialog open={!!photo} onOpenChange={(o) => !o && setPhoto(null)}>
                <DialogContent className="max-w-4xl p-2">
                  <DialogTitle className="sr-only">Event photo</DialogTitle>
                  {photo && <img src={cloudinary(photo, 'f_auto,q_auto,w_1600')} alt="Event photo" className="w-full rounded-lg" />}
                </DialogContent>
              </Dialog>
            </Section>
          )}
          {event.venue && !event.online && (
            <Section title="Venue">
              <VenueMap venue={event.venue} />
            </Section>
          )}
          <Section title="Organizer">
            <Link to={`/o/${event.organizer.slug}`} className="flex items-center gap-4 rounded-2xl border border-border bg-card p-4 transition-colors hover:border-primary/40">
              {event.organizer.logoUrl ? (
                <img src={cloudinary(event.organizer.logoUrl, 'f_auto,q_auto,w_120')} alt="" className="size-12 rounded-xl object-cover" />
              ) : (
                <span className="bg-gradient-brand grid size-12 place-items-center rounded-xl font-bold text-white">{event.organizer.name[0]}</span>
              )}
              <span className="min-w-0">
                <span className="block font-semibold">{event.organizer.name}</span>
                {event.organizer.bio && <span className="line-clamp-1 text-sm text-muted-foreground">{event.organizer.bio}</span>}
              </span>
            </Link>
          </Section>
          <Section title="Refunds">
            <p className="text-muted-foreground">{refundText(event)}</p>
          </Section>
          {event.terms && (
            <Section title="Terms & conditions">
              <p className="whitespace-pre-line text-sm text-muted-foreground">{event.terms}</p>
            </Section>
          )}
          <div className="border-t border-border pt-4">
            <ReportEventLink eventId={event.id} />
          </div>
        </div>

        <aside className="hidden lg:block">
          <div className="sticky top-24 space-y-2 rounded-3xl border border-border bg-card p-6 shadow-[var(--shadow-card)]">
            <h2 className="font-sans text-lg font-semibold tracking-normal">Tickets</h2>
            <TicketPicker event={event} />
          </div>
        </aside>
      </div>

      <section id="tickets" className="mx-auto max-w-7xl scroll-mt-24 px-4 pb-10 sm:px-6 lg:hidden" aria-label="Tickets">
        <h2 className="mb-2 text-xl font-bold">Tickets</h2>
        <TicketPicker event={event} />
      </section>

      {/* Sits directly above the mobile tab bar (64px + iOS safe area); at md+ there is no tab bar, so it docks to the bottom. */}
      <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-30 border-t border-border bg-background/95 px-4 py-3 backdrop-blur-xl md:bottom-0 md:pb-[calc(0.75rem+env(safe-area-inset-bottom))] lg:hidden">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3">
          <span className="font-semibold">{fromPrice}</span>
          <Button
            className="rounded-full px-6"
            disabled={!canBook(event)}
            onClick={() => document.getElementById('tickets')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
          >
            {bookLabel(event)}
          </Button>
        </div>
      </div>
    </article>
  )
}
