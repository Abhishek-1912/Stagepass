import { ArrowLeft, ChevronLeft, ChevronRight, Sun } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import type { TicketLine } from '@/features/checkout/types'
import { formatEventDate } from '@/lib/datetime'
import { cn } from '@/lib/utils'
import { useMyBooking } from './api'
import { TicketQr } from './TicketQr'

const STATUS: Record<TicketLine['status'], string> = {
  VALID: 'Valid',
  CHECKED_IN: 'Checked in',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
}

/** `/account/bookings/:id/tickets/:ticketId` — one ticket, big enough to scan at the door. */
export function TicketViewPage() {
  const { id, ticketId } = useParams()
  const booking = useMyBooking(id)

  if (booking.isPending) return <PageLoader label="Loading your ticket" />
  const b = booking.data
  const index = b ? b.tickets.findIndex((t) => t.id === ticketId) : -1
  if (!b || index < 0) {
    return <p className="mx-auto max-w-md px-4 py-16 text-center text-muted-foreground">We couldn’t find this ticket.</p>
  }
  const t = b.tickets[index]
  const prev = b.tickets[index - 1]
  const next = b.tickets[index + 1]
  const nav = 'inline-flex size-10 items-center justify-center rounded-full border border-border hover:bg-muted'

  return (
    <div className="mx-auto max-w-md space-y-5 px-4 py-6">
      <Link to={`/account/bookings/${b.id}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden /> Booking {b.code}
      </Link>

      <article className="overflow-hidden rounded-3xl border border-border bg-card shadow-[var(--shadow-card)]">
        <header className="bg-gradient-to-br from-primary to-brand-blue px-6 py-5 text-white">
          <h1 className="text-xl font-bold leading-tight">{b.eventTitle}</h1>
          <p className="text-sm opacity-90">{formatEventDate(b.eventStartsAt)}</p>
          {b.venueName && (
            <p className="text-sm opacity-90">
              {b.venueName}, {b.city}
            </p>
          )}
        </header>
        <div className="flex flex-col items-center gap-3 px-6 py-6 text-center">
          {t.qrToken ? (
            <TicketQr token={t.qrToken} size={260} label={`QR code for ${t.code}`} />
          ) : (
            <div className="grid size-[260px] place-items-center rounded-xl bg-muted text-sm text-muted-foreground">
              This ticket can’t be used for entry.
            </div>
          )}
          <p className="font-mono text-lg font-semibold">{t.code}</p>
          <p className="text-sm text-muted-foreground">
            {t.ticketTypeName} · {t.holderName}
          </p>
          <span
            className={cn(
              'rounded-full px-3 py-1 text-xs font-semibold',
              t.status === 'VALID' ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' : 'bg-muted text-muted-foreground',
            )}
          >
            {STATUS[t.status]}
          </span>
        </div>
      </article>

      {t.qrToken && (
        <p className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
          <Sun className="size-4" aria-hidden /> Turn your screen brightness up at the entrance
        </p>
      )}

      {b.tickets.length > 1 && (
        <nav className="flex items-center justify-between" aria-label="Tickets in this booking">
          {prev ? (
            <Link to={`/account/bookings/${b.id}/tickets/${prev.id}`} className={nav} aria-label="Previous ticket">
              <ChevronLeft className="size-5" />
            </Link>
          ) : (
            <span className="size-10" />
          )}
          <span className="text-sm text-muted-foreground">
            {index + 1} of {b.tickets.length}
          </span>
          {next ? (
            <Link to={`/account/bookings/${b.id}/tickets/${next.id}`} className={nav} aria-label="Next ticket">
              <ChevronRight className="size-5" />
            </Link>
          ) : (
            <span className="size-10" />
          )}
        </nav>
      )}
    </div>
  )
}
