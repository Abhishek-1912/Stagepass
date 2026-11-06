import { Minus, Plus } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import type { PublicEventDetail, PublicTicketType } from '@/features/discovery/types'
import { bookLabel, canBook } from '@/features/event/bookLabel'
import { TicketList } from '@/features/event/TicketList'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { useQuote } from './api'
import { serializeItems } from './cart'
import { PriceSummary } from './PriceSummary'
import type { CartItem } from './types'

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}

function Stepper({ type, quantity, canAdd, onChange }: { type: PublicTicketType; quantity: number; canAdd: boolean; onChange: (q: number) => void }) {
  const down = () => onChange(quantity <= type.minPerOrder ? 0 : quantity - 1)
  const up = () => onChange(quantity === 0 ? type.minPerOrder : quantity + 1)
  return (
    <div className="flex items-center gap-2" role="group" aria-label={`${type.name} quantity`}>
      <Button type="button" variant="outline" size="icon" className="size-8 rounded-full" onClick={down} disabled={quantity === 0} aria-label={`Remove ${type.name}`}>
        <Minus className="size-4" />
      </Button>
      <span className="w-6 text-center font-semibold tabular-nums" aria-live="polite">
        {quantity}
      </span>
      <Button type="button" variant="outline" size="icon" className="size-8 rounded-full" onClick={up} disabled={!canAdd} aria-label={`Add ${type.name}`}>
        <Plus className="size-4" />
      </Button>
    </div>
  )
}

/** Ticket types with quantity steppers, a live server quote and "Continue" to checkout. */
export function TicketPicker({ event }: { event: PublicEventDetail }) {
  const navigate = useNavigate()
  const [qty, setQty] = useState<Record<string, number>>({})
  const bookable = canBook(event)

  const items: CartItem[] = useMemo(
    () => event.ticketTypes.filter((t) => (qty[t.id] ?? 0) > 0).map((t) => ({ ticketTypeId: t.id, quantity: qty[t.id] })),
    [event.ticketTypes, qty],
  )
  const total = items.reduce((n, i) => n + i.quantity, 0)
  const limit = event.maxTicketsPerUser ?? Infinity
  const debouncedItems = useDebounced(items, 250)
  const quote = useQuote(bookable ? event.id : undefined, debouncedItems)

  const canAdd = (t: PublicTicketType) => {
    const current = qty[t.id] ?? 0
    const step = current === 0 ? t.minPerOrder : 1
    return current + step <= t.maxPerOrder && total + step <= limit
  }

  const control = (t: PublicTicketType) =>
    bookable && t.availability === 'ON_SALE' ? (
      <Stepper type={t} quantity={qty[t.id] ?? 0} canAdd={canAdd(t)} onChange={(q) => setQty((prev) => ({ ...prev, [t.id]: q }))} />
    ) : null

  const quoteError = quote.error && isApiError(quote.error) ? (quote.error.fieldErrors[0]?.message ?? quote.error.detail) : null
  const showQuote = items.length > 0 && quote.data && !quoteError

  return (
    <div className="space-y-4">
      <TicketList event={event} control={control} />
      {showQuote && <PriceSummary price={quote.data!.price} />}
      {items.length > 0 && quoteError && (
        <p role="alert" className="text-sm text-destructive">
          {quoteError}
        </p>
      )}
      <Button
        size="lg"
        className="w-full rounded-full"
        disabled={!bookable || items.length === 0 || !!quoteError}
        onClick={() => navigate(`/checkout/${event.slug}?items=${serializeItems(items)}`)}
      >
        {!bookable
          ? bookLabel(event)
          : items.length === 0
            ? 'Select tickets'
            : showQuote
              ? `Continue · ${formatPaiseExact(quote.data!.price.totalPaise)}`
              : 'Continue'}
      </Button>
      {event.maxTicketsPerUser && (
        <p className="text-center text-xs text-muted-foreground">Up to {event.maxTicketsPerUser} tickets per person</p>
      )}
    </div>
  )
}
