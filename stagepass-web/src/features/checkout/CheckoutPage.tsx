import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CalendarDays, MapPin } from 'lucide-react'
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMe } from '@/features/auth/useMe'
import { usePublicEvent } from '@/features/discovery/api'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { formatEventDate } from '@/lib/datetime'
import { INDIAN_STATES } from '@/lib/indianStates'
import { useCreateBooking, useMyBooking, useQuote, useVerifyPayment } from './api'
import { parseItems } from './cart'
import { HoldCountdown } from './HoldCountdown'
import { forgetIdempotencyKey, idempotencyKeyFor } from './idempotency'
import { PriceSummary } from './PriceSummary'
import { CheckoutDismissed, openRazorpay, type Prefill } from './razorpay'
import type { Booking } from './types'

const MOBILE = /^[6-9]\d{9}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

interface Attendee {
  attendeeName: string
  attendeeEmail: string
  attendeePhone: string
  buyerState: string
}

/** Opens Razorpay for a pending booking, verifies on success, and routes the buyer to the right place. */
function usePayForBooking() {
  const verify = useVerifyPayment()
  const navigate = useNavigate()
  const pay = async (booking: Booking, prefill: Prefill, onPaid?: () => void) => {
    if (!booking.checkout) return
    let result
    try {
      result = await openRazorpay(booking.checkout, prefill, booking.eventTitle)
    } catch (e) {
      if (e instanceof CheckoutDismissed) {
        if (e.lastFailure) toast.error(e.lastFailure)
      } else {
        toast.error(e instanceof Error ? e.message : 'Payment could not be started')
      }
      navigate(`/checkout/booking/${booking.id}`)
      return
    }
    try {
      const confirmed = await verify.mutateAsync({ bookingId: booking.id, ...result })
      onPaid?.()
      navigate(`/account/bookings/${confirmed.id}?status=success`)
    } catch {
      // The money is with Razorpay; the webhook will confirm the booking even if this call failed.
      onPaid?.()
      navigate(`/account/bookings/${booking.id}?status=processing`)
    }
  }
  return { pay, verifying: verify.isPending }
}

function EventHeader({ title, startsAt, place }: { title: string; startsAt: string; place?: string | null }) {
  return (
    <div className="space-y-1">
      <h1 className="text-2xl font-bold sm:text-3xl">{title}</h1>
      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
        <CalendarDays className="size-4" aria-hidden /> {formatEventDate(startsAt)}
      </p>
      {place && (
        <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <MapPin className="size-4" aria-hidden /> {place}
        </p>
      )}
    </div>
  )
}

/** `/checkout/:slug?items=…` — attendee details, price, then Razorpay. */
export function CheckoutPage() {
  const { slug = '' } = useParams()
  const [params] = useSearchParams()
  const items = useMemo(() => parseItems(params.get('items')), [params])
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const event = usePublicEvent(slug)
  const quote = useQuote(event.data?.id, items)
  const me = useMe()
  const create = useCreateBooking()
  const { pay, verifying } = usePayForBooking()

  // What the buyer typed; anything untouched falls back to their profile.
  const [edited, setEdited] = useState<Partial<Attendee>>({})
  const [errors, setErrors] = useState<Partial<Record<keyof Attendee, string>>>({})
  const form: Attendee = {
    attendeeName: edited.attendeeName ?? me.data?.fullName ?? '',
    attendeeEmail: edited.attendeeEmail ?? me.data?.email ?? '',
    attendeePhone: edited.attendeePhone ?? me.data?.phone ?? '',
    buyerState: edited.buyerState ?? '',
  }

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-lg px-4 py-16 text-center">
        <p className="mb-4 text-muted-foreground">Pick your tickets first.</p>
        <Button asChild className="rounded-full">
          <Link to={`/events/${slug}`}>Back to the event</Link>
        </Button>
      </div>
    )
  }
  if (event.isPending || (quote.isPending && !quote.error)) return <PageLoader label="Preparing checkout" />
  if (event.error || !event.data) {
    return <p className="px-4 py-16 text-center text-muted-foreground">This event is not available.</p>
  }

  const quoteError = quote.error && isApiError(quote.error) ? (quote.error.fieldErrors[0]?.message ?? quote.error.detail) : null
  const price = quote.data?.price
  const set = (k: keyof Attendee) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setEdited((f) => ({ ...f, [k]: e.target.value }))

  const validate = () => {
    const next: typeof errors = {}
    if (!form.attendeeName.trim()) next.attendeeName = 'Enter the attendee’s name'
    if (!EMAIL.test(form.attendeeEmail.trim())) next.attendeeEmail = 'Enter a valid email - tickets are sent here'
    if (form.attendeePhone && !MOBILE.test(form.attendeePhone)) next.attendeePhone = 'Enter a 10-digit Indian mobile number'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!validate() || !price) return
    const body = {
      eventId: event.data.id,
      items,
      attendeeName: form.attendeeName.trim(),
      attendeeEmail: form.attendeeEmail.trim(),
      attendeePhone: form.attendeePhone || undefined,
      buyerState: form.buyerState || undefined,
    }
    let booking: Booking
    try {
      booking = await create.mutateAsync({ key: idempotencyKeyFor(slug, items), body })
      if (booking.status === 'EXPIRED' || booking.status === 'CANCELLED') {
        // This tab's key belonged to an earlier, finished checkout of the same cart: start a new one.
        forgetIdempotencyKey(slug, items)
        booking = await create.mutateAsync({ key: idempotencyKeyFor(slug, items), body })
      }
    } catch (err) {
      if (isApiError(err) && err.code === 'SOLD_OUT') {
        toast.error(err.detail)
        void queryClient.invalidateQueries({ queryKey: ['public', 'event', slug] })
        void queryClient.invalidateQueries({ queryKey: ['quote'] })
      } else if (isApiError(err) && err.status === 503) {
        toast.error('Payments are temporarily unavailable. Please try again in a minute.')
      } else if (isApiError(err) && err.fieldErrors.length > 0) {
        const fieldErrors: typeof errors = {}
        for (const fe of err.fieldErrors) {
          if (fe.field in form) fieldErrors[fe.field as keyof Attendee] = fe.message
        }
        setErrors(fieldErrors)
        if (Object.keys(fieldErrors).length === 0) toast.error(err.fieldErrors[0].message)
      } else {
        toast.error(isApiError(err) ? err.detail : 'Could not start checkout')
      }
      return
    }
    if (booking.status !== 'PENDING_PAYMENT') {
      forgetIdempotencyKey(slug, items)
      navigate(`/account/bookings/${booking.id}?status=success`)
      return
    }
    await pay(booking, { name: body.attendeeName, email: body.attendeeEmail, contact: body.attendeePhone }, () =>
      forgetIdempotencyKey(slug, items),
    )
  }

  const busy = create.isPending || verifying
  const e = event.data
  return (
    <div className="mx-auto grid max-w-5xl gap-8 px-4 py-8 sm:px-6 lg:grid-cols-[1fr_360px]">
      <div className="space-y-6">
        <Link to={`/events/${slug}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" aria-hidden /> Back to event
        </Link>
        <EventHeader title={e.title} startsAt={e.startsAt} place={e.venue ? `${e.venue.name}, ${e.venue.city}` : 'Online'} />

        <form id="checkout-form" onSubmit={submit} noValidate className="space-y-4 rounded-3xl border border-border bg-card p-6">
          <h2 className="font-sans text-lg font-semibold tracking-normal">Who’s attending?</h2>
          <Field id="attendeeName" label="Full name" error={errors.attendeeName}>
            <Input id="attendeeName" autoComplete="name" value={form.attendeeName} onChange={set('attendeeName')} aria-invalid={!!errors.attendeeName} />
          </Field>
          <Field id="attendeeEmail" label="Email" hint="Tickets and receipts are sent here" error={errors.attendeeEmail}>
            <Input id="attendeeEmail" type="email" autoComplete="email" value={form.attendeeEmail} onChange={set('attendeeEmail')} aria-invalid={!!errors.attendeeEmail} />
          </Field>
          <Field id="attendeePhone" label="Mobile (optional)" error={errors.attendeePhone}>
            <Input id="attendeePhone" inputMode="numeric" autoComplete="tel-national" maxLength={10} value={form.attendeePhone} onChange={set('attendeePhone')} aria-invalid={!!errors.attendeePhone} />
          </Field>
          <Field id="buyerState" label="State (for your GST invoice, optional)">
            <select id="buyerState" value={form.buyerState} onChange={set('buyerState')} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
              <option value="">Select state</option>
              {INDIAN_STATES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </Field>
        </form>
      </div>

      <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
        <div className="space-y-4 rounded-3xl border border-border bg-card p-6 shadow-[var(--shadow-card)]">
          <h2 className="font-sans text-lg font-semibold tracking-normal">Order summary</h2>
          {quoteError ? (
            <div className="space-y-3">
              <p role="alert" className="text-sm text-destructive">
                {quoteError}
              </p>
              <Button asChild variant="outline" className="w-full rounded-full">
                <Link to={`/events/${slug}`}>Change tickets</Link>
              </Button>
            </div>
          ) : (
            price && <PriceSummary lines={quote.data?.lines} price={price} />
          )}
          <Button type="submit" form="checkout-form" size="lg" className="w-full rounded-full" disabled={busy || !price || !!quoteError}>
            {busy ? 'Working…' : price && price.totalPaise === 0 ? 'Get free tickets' : price ? `Pay ${formatPaiseExact(price.totalPaise)}` : 'Pay'}
          </Button>
          {price && price.totalPaise > 0 && (
            <p className="text-center text-xs text-muted-foreground">
              Tickets are held for {quote.data?.holdMinutes ?? 10} minutes while you pay. Secure payment by Razorpay.
            </p>
          )}
        </div>
      </aside>
    </div>
  )
}

function Field({ id, label, hint, error, children }: { id: string; label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p className="text-sm text-destructive">{error}</p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}

/** `/checkout/booking/:bookingId` — come back to a pending booking and pay for it while the hold lasts. */
export function ResumeCheckoutPage() {
  const { bookingId } = useParams()
  const booking = useMyBooking(bookingId)
  const { pay, verifying } = usePayForBooking()
  const [expired, setExpired] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    if (booking.data && booking.data.status !== 'PENDING_PAYMENT' && booking.data.status !== 'EXPIRED') {
      navigate(`/account/bookings/${booking.data.id}`, { replace: true })
    }
  }, [booking.data, navigate])

  if (booking.isPending) return <PageLoader label="Loading your booking" />
  if (booking.error || !booking.data) {
    return <p className="px-4 py-16 text-center text-muted-foreground">We couldn’t find this booking.</p>
  }
  const b = booking.data
  const over = expired || b.status === 'EXPIRED' || !b.checkout
  return (
    <div className="mx-auto max-w-lg space-y-6 px-4 py-10">
      <EventHeader title={b.eventTitle} startsAt={b.eventStartsAt} place={b.venueName ? `${b.venueName}, ${b.city}` : null} />
      <div className="space-y-4 rounded-3xl border border-border bg-card p-6 shadow-[var(--shadow-card)]">
        <PriceSummary lines={b.items} price={b.price} />
        {over ? (
          <div className="space-y-3 text-center">
            <p className="font-medium">Hold expired - start again</p>
            <p className="text-sm text-muted-foreground">Your tickets were released so others could book them.</p>
            <Button asChild className="w-full rounded-full">
              <Link to={`/events/${b.eventSlug}`}>Choose tickets again</Link>
            </Button>
          </div>
        ) : (
          <>
            {b.holdExpiresAt && <HoldCountdown expiresAt={b.holdExpiresAt} onExpire={() => setExpired(true)} />}
            <Button
              size="lg"
              className="w-full rounded-full"
              disabled={verifying}
              onClick={() => pay(b, { name: b.attendeeName, email: b.attendeeEmail })}
            >
              {verifying ? 'Confirming…' : `Pay ${formatPaiseExact(b.checkout!.amountPaise)}`}
            </Button>
          </>
        )}
      </div>
    </div>
  )
}
