import { formatPaise, formatPaiseExact as money } from '@/lib/money'
import type { PriceBreakdown, QuoteLine } from './types'

export function PriceSummary({ lines, price }: { lines?: QuoteLine[]; price: PriceBreakdown }) {
  const free = price.totalPaise === 0
  return (
    <dl className="space-y-1.5 text-sm">
      {lines?.map((l) => (
        <div key={l.ticketTypeId} className="flex justify-between gap-3">
          <dt className="text-muted-foreground">
            {l.name} × {l.quantity}
          </dt>
          <dd>{money(l.amountPaise)}</dd>
        </div>
      ))}
      {!free && (
        <>
          {!lines && (
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Tickets</dt>
              <dd>{money(price.subtotalPaise)}</dd>
            </div>
          )}
          <div className="flex justify-between gap-3">
            <dt className="text-muted-foreground">Convenience fee</dt>
            <dd>{money(price.feePaise)}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-muted-foreground">GST on fee</dt>
            <dd>{money(price.feeGstPaise)}</dd>
          </div>
        </>
      )}
      <div className="flex justify-between gap-3 border-t border-border pt-2 text-base font-semibold">
        <dt>Total</dt>
        <dd data-testid="price-total">{free ? formatPaise(0) : money(price.totalPaise)}</dd>
      </div>
    </dl>
  )
}

