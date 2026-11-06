import type { ReactNode } from 'react'
import type { PublicEventDetail, PublicTicketType } from '@/features/discovery/types'
import { formatPaise } from '@/lib/money'
import { cn } from '@/lib/utils'

const saleDate = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short' })

function Availability({ t }: { t: PublicTicketType }) {
  const pill = 'rounded-full px-2 py-0.5 text-xs font-semibold'
  switch (t.availability) {
    case 'SOLD_OUT':
      return <span className={cn(pill, 'bg-muted text-muted-foreground')}>Sold out</span>
    case 'ENDED':
      return <span className={cn(pill, 'bg-muted text-muted-foreground')}>Sales ended</span>
    case 'NOT_STARTED':
      return (
        <span className={cn(pill, 'bg-secondary text-secondary-foreground')}>
          Sales open {t.saleStartsAt ? saleDate.format(new Date(t.saleStartsAt)) : 'soon'}
        </span>
      )
    default:
      return t.fewLeft ? <span className={cn(pill, 'bg-brand-coral/15 text-brand-coral')}>Few left</span> : null
  }
}

/** Ticket types with availability; {@code control} renders a quantity picker for each row that can be bought. */
export function TicketList({ event, control }: { event: PublicEventDetail; control?: (t: PublicTicketType) => ReactNode }) {
  return (
    <ul className="divide-y divide-border">
      {event.ticketTypes.map((t) => {
        const muted = t.availability === 'SOLD_OUT' || t.availability === 'ENDED'
        return (
          <li key={t.id} className={cn('flex items-start justify-between gap-3 py-3', muted && 'opacity-60')}>
            <div className="min-w-0">
              <p className="font-medium">{t.name}</p>
              {t.description && <p className="text-sm text-muted-foreground">{t.description}</p>}
              {event.status === 'PUBLISHED' && (
                <div className="mt-1">
                  <Availability t={t} />
                </div>
              )}
            </div>
            <div className="flex shrink-0 flex-col items-end gap-2">
              <p className="font-semibold">{formatPaise(t.pricePaise)}</p>
              {control?.(t)}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
