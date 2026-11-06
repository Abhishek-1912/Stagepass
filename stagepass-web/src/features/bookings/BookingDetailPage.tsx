import { ArrowLeft, CalendarDays, CheckCircle2, Clock, Download, MapPin, QrCode } from 'lucide-react'
import { toast } from 'sonner'
import { Link, useParams, useSearchParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { HoldCountdown } from '@/features/checkout/HoldCountdown'
import { PriceSummary } from '@/features/checkout/PriceSummary'
import type { Booking, RefundLine } from '@/features/checkout/types'
import { isApiError } from '@/lib/api/ApiError'
import { formatEventDate } from '@/lib/datetime'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { useMyBooking } from './api'
import { BookingStatusBadge } from './BookingStatusBadge'
import { downloadFile } from './downloads'
import { RefundDialog } from './RefundDialog'
import { TicketQr } from './TicketQr'

async function save(path: string, fallbackName: string) {
  try {
    await downloadFile(path, fallbackName)
  } catch (e) {
    toast.error(isApiError(e) ? e.detail : 'Could not download the file')
  }
}

const REFUND_STATUS: Record<RefundLine['status'], string> = {
  REQUESTED: 'Starting',
  PENDING_APPROVAL: 'Waiting for the organizer',
  DECLINED: 'Declined',
  PROCESSING: 'On its way',
  PROCESSED: 'Refunded',
  FAILED: 'Failed - please try again',
}

const TICKET_STATUS = { VALID: 'Valid', CHECKED_IN: 'Used', CANCELLED: 'Cancelled', REFUNDED: 'Refunded' } as const

const dateTime = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

function Banner({ status, booking }: { status: string | null; booking: Booking }) {
  if (status === 'success' && booking.status === 'CONFIRMED') {
    return (
      <div role="status" className="flex items-center gap-3 rounded-2xl bg-emerald-500/10 p-4 text-emerald-800 dark:text-emerald-200">
        <CheckCircle2 className="size-6 shrink-0" aria-hidden />
        <div>
          <p className="font-semibold">You’re going! Booking {booking.code} is confirmed.</p>
          <p className="text-sm">We’ve saved your tickets here. Show them at the entrance.</p>
        </div>
      </div>
    )
  }
  if ((status === 'processing' || status === 'success') && booking.status === 'PENDING_PAYMENT') {
    return (
      <div role="status" className="flex items-center gap-3 rounded-2xl bg-amber-500/10 p-4 text-amber-800 dark:text-amber-200">
        <Clock className="size-6 shrink-0" aria-hidden />
        <p>We’re confirming your payment with the bank. This page updates in a moment - you won’t be charged twice.</p>
      </div>
    )
  }
  return null
}

function RefundSection({ booking }: { booking: Booking }) {
  const refund = booking.refund
  if (!refund) return null
  return (
    <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
      <h2 className="font-sans text-lg font-semibold tracking-normal">Refunds</h2>
      {refund.requestable ? (
        <>
          <p className="text-sm text-muted-foreground">{refund.message}</p>
          <RefundDialog
            booking={booking}
            trigger={
              <Button variant="outline" className="rounded-full">
                {refund.selfServe ? 'Refund tickets' : 'Request a refund'}
              </Button>
            }
          />
        </>
      ) : (
        booking.status !== 'REFUNDED' && <p className="text-sm text-muted-foreground">{refund.message}</p>
      )}
      {refund.history.length > 0 && (
        <ul className="divide-y divide-border text-sm">
          {refund.history.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span>
                {r.ticketCount} {r.ticketCount === 1 ? 'ticket' : 'tickets'} · {formatPaiseExact(r.amountPaise)}
                <span className="text-muted-foreground"> · {dateTime.format(new Date(r.createdAt))}</span>
              </span>
              <span className={cn('font-medium', r.status === 'FAILED' || r.status === 'DECLINED' ? 'text-brand-coral' : '')}>
                {REFUND_STATUS[r.status]}
              </span>
              {r.note && <p className="w-full text-muted-foreground">Organizer: {r.note}</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** `/account/bookings/:id` — tickets, payment, refunds. */
export function BookingDetailPage() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const status = params.get('status')
  const booking = useMyBooking(id, { pollWhilePending: status === 'processing' || status === 'success' })

  if (booking.isPending) return <PageLoader label="Loading your booking" />
  if (booking.error || !booking.data) {
    return (
      <p role="alert" className="mx-auto max-w-xl px-4 py-16 text-center text-muted-foreground">
        {isApiError(booking.error) && booking.error.status === 404 ? 'We couldn’t find this booking.' : 'We couldn’t load this booking.'}
      </p>
    )
  }
  const b = booking.data
  const pending = b.status === 'PENDING_PAYMENT'

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
      <Link to="/account/bookings" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden /> My tickets
      </Link>
      <Banner status={status} booking={b} />

      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <BookingStatusBadge status={b.status} />
          <span className="font-mono text-sm text-muted-foreground">{b.code}</span>
        </div>
        <h1 className="text-2xl font-bold sm:text-3xl">
          <Link to={`/events/${b.eventSlug}`} className="hover:underline">
            {b.eventTitle}
          </Link>
        </h1>
        <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <CalendarDays className="size-4" aria-hidden /> {formatEventDate(b.eventStartsAt)}
        </p>
        {b.venueName && (
          <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <MapPin className="size-4" aria-hidden /> {b.venueName}, {b.city}
          </p>
        )}
      </header>

      {pending && b.checkout && (
        <section className="space-y-3 rounded-3xl border border-amber-500/40 bg-amber-500/5 p-6">
          <p className="font-medium">Payment not finished</p>
          {b.holdExpiresAt && <HoldCountdown expiresAt={b.holdExpiresAt} />}
          <Button asChild className="rounded-full">
            <Link to={`/checkout/booking/${b.id}`}>Complete payment</Link>
          </Button>
        </section>
      )}

      {b.tickets.length > 0 && (
        <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
          <h2 className="font-sans text-lg font-semibold tracking-normal">Your tickets</h2>
          <ul className="divide-y divide-border">
            {b.tickets.map((t) => (
              <li key={t.id} className={cn('flex items-center justify-between gap-3 py-3', t.status !== 'VALID' && t.status !== 'CHECKED_IN' && 'opacity-60')}>
                <div className="flex items-center gap-3">
                  {t.qrToken && (
                    <Link to={`/account/bookings/${b.id}/tickets/${t.id}`} aria-label={`Show ticket ${t.code}`}>
                      <TicketQr token={t.qrToken} size={64} label={`QR code for ${t.code}`} className="border border-border p-1" />
                    </Link>
                  )}
                  <div>
                    <p className="font-mono font-semibold">{t.code}</p>
                    <p className="text-sm text-muted-foreground">
                      {t.ticketTypeName} · {t.holderName}
                    </p>
                  </div>
                </div>
                <span className="text-sm font-medium">{TICKET_STATUS[t.status]}</span>
              </li>
            ))}
          </ul>
          {b.tickets.some((t) => t.qrToken) && (
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" className="rounded-full" onClick={() => save(`/me/bookings/${b.id}/tickets.pdf`, `stagepass-${b.code}.pdf`)}>
                <Download className="size-4" aria-hidden /> Download tickets (PDF)
              </Button>
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <QrCode className="size-4" aria-hidden /> Tap a QR to show it full screen at the entrance.
              </p>
            </div>
          )}
        </section>
      )}

      <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
        <h2 className="font-sans text-lg font-semibold tracking-normal">Payment</h2>
        <PriceSummary lines={b.items} price={b.price} />
        {b.invoiceNumber && (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs text-muted-foreground">GST invoice {b.invoiceNumber}</p>
            <Button variant="ghost" size="sm" className="rounded-full" onClick={() => save(`/me/bookings/${b.id}/invoice.pdf`, 'stagepass-invoice.pdf')}>
              <Download className="size-4" aria-hidden /> Download invoice
            </Button>
          </div>
        )}
      </section>

      <RefundSection booking={b} />
    </div>
  )
}
