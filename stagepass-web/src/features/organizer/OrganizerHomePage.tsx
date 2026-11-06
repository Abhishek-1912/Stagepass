import { CalendarPlus, IndianRupee, Radio, Ticket, Wallet } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { useOverview } from './dashboard/api'
import { KpiCard } from './dashboard/KpiCard'
import { kpiMoney } from './dashboard/kpiMoney'
import { RangePicker } from './dashboard/RangePicker'
import { SalesChart } from './dashboard/SalesChart'
import type { Range } from './dashboard/types'

const when = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

/** Organizer Overview: KPIs across all events, daily sales and the latest bookings. */
export function OrganizerHomePage() {
  const [days, setDays] = useState<Range>(30)
  const [metric, setMetric] = useState<'amount' | 'tickets'>('amount')
  const overview = useOverview(days)

  const header = (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h2 className="text-2xl font-bold">Welcome back</h2>
        <p className="text-muted-foreground">Here’s how your events are doing.</p>
      </div>
      <Button asChild className="rounded-full">
        <Link to="/organizer/events/new">
          <CalendarPlus /> Create event
        </Link>
      </Button>
    </div>
  )

  if (overview.isPending) {
    return (
      <div className="space-y-8">
        {header}
        <PageLoader label="Loading your dashboard" />
      </div>
    )
  }
  if (overview.error || !overview.data) {
    return (
      <div className="space-y-8">
        {header}
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">
          {isApiError(overview.error) ? overview.error.detail : 'We couldn’t load your dashboard.'}
        </p>
      </div>
    )
  }
  const o = overview.data

  return (
    <div className="space-y-8">
      {header}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Live events" value={String(o.liveEvents)} hint={`${o.upcomingEvents} upcoming`} icon={Radio} />
        <KpiCard label="Tickets sold" value={String(o.ticketsSold)} icon={Ticket} />
        <KpiCard label="Net sales" value={kpiMoney(o.netSalesPaise)} hint="Ticket sales after refunds" icon={IndianRupee} />
        <KpiCard label="Earnings" value={kpiMoney(o.earningsPaise)} hint="After commission" icon={Wallet} />
      </div>

      <section className="space-y-4 rounded-3xl border border-border bg-card p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="inline-flex gap-1 rounded-full border border-border p-1" role="radiogroup" aria-label="Chart metric">
            {(['amount', 'tickets'] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="radio"
                aria-checked={metric === m}
                onClick={() => setMetric(m)}
                className={cn(
                  'rounded-full px-3 py-1 text-xs font-medium',
                  metric === m ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {m === 'amount' ? 'Sales' : 'Tickets'}
              </button>
            ))}
          </div>
          <RangePicker value={days} onChange={setDays} />
        </div>
        <SalesChart points={o.dailySales} metric={metric} />
      </section>

      <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
        <h3 className="font-sans text-lg font-semibold tracking-normal">Recent bookings</h3>
        {o.recentBookings.length === 0 ? (
          <p className="text-sm text-muted-foreground">No bookings yet. Share your event page to start selling.</p>
        ) : (
          <ul className="divide-y divide-border">
            {o.recentBookings.map((b) => (
              <li key={b.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
                <div className="min-w-0">
                  <Link to={`/organizer/events/${b.eventId}/manage?tab=bookings`} className="font-mono font-semibold hover:underline">
                    {b.code}
                  </Link>
                  <p className="truncate text-muted-foreground">
                    {b.attendeeName} · {b.eventTitle}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-medium tabular-nums">{formatPaiseExact(b.totalPaise)}</p>
                  <p className="text-xs text-muted-foreground">
                    {b.ticketCount} {b.ticketCount === 1 ? 'ticket' : 'tickets'} · {when.format(new Date(b.confirmedAt))}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
