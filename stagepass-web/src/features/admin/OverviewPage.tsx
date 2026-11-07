import { IndianRupee, ReceiptIndianRupee, Ticket, Undo2, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { KpiCard } from '@/features/organizer/dashboard/KpiCard'
import { kpiMoney } from '@/features/organizer/dashboard/kpiMoney'
import { RangePicker } from '@/features/organizer/dashboard/RangePicker'
import { SalesChart } from '@/features/organizer/dashboard/SalesChart'
import type { Range } from '@/features/organizer/dashboard/types'
import { ErrorState } from './AdminTable'
import { useAdminOverview } from './api'

function plural(n: number, one: string, many: string) {
  return `${n} ${n === 1 ? one : many}`
}

/** Platform numbers and today's queues (spec 9 admin Overview). */
export function OverviewPage() {
  const [days, setDays] = useState<Range>(30)
  const { data: o, isPending, isError } = useAdminOverview(days)

  if (isPending) return <PageLoader />
  if (isError || !o) return <ErrorState>We couldn’t load the overview. Refresh to try again.</ErrorState>

  const attention = [
    { to: '/admin/organizers?tab=applications', label: plural(o.pendingApplications, 'organizer application', 'organizer applications'), n: o.pendingApplications },
    { to: '/admin/events', label: plural(o.pendingEvents, 'event to review', 'events to review'), n: o.pendingEvents },
    { to: '/admin/reports', label: plural(o.openReports, 'open report', 'open reports'), n: o.openReports },
    { to: '/admin/transactions?tab=refunds', label: plural(o.failedRefunds, 'failed refund', 'failed refunds'), n: o.failedRefunds },
    { to: '/admin/transactions?tab=disputes', label: plural(o.openDisputes, 'open dispute', 'open disputes'), n: o.openDisputes },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">Overview</h2>
          <p className="text-muted-foreground">
            Last {days} days · {o.liveEvents} live events · {o.totalUsers} users
          </p>
        </div>
        <RangePicker value={days} onChange={setDays} />
      </div>

      <section aria-label="Needs attention" className="flex flex-wrap gap-2">
        {attention.map((a) => (
          <Link
            key={a.to}
            to={a.to}
            className={
              a.n > 0
                ? 'rounded-full bg-accent px-3.5 py-1.5 text-sm font-medium text-accent-foreground hover:opacity-90'
                : 'rounded-full border border-border px-3.5 py-1.5 text-sm text-muted-foreground hover:bg-muted'
            }
          >
            {a.label}
          </Link>
        ))}
      </section>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <KpiCard label="GMV" value={kpiMoney(o.gmvPaise)} hint={`${o.bookings} bookings`} icon={ReceiptIndianRupee} />
        <KpiCard label="Platform revenue" value={kpiMoney(o.platformRevenuePaise)} hint="Fees + commission − gateway" icon={IndianRupee} />
        <KpiCard label="Refunds" value={kpiMoney(o.refundsPaise)} icon={Undo2} />
        <KpiCard label="Tickets sold" value={String(o.ticketsSold)} icon={Ticket} />
        <KpiCard label="New users" value={String(o.newUsers)} icon={UserPlus} />
      </div>

      <section className="rounded-2xl border border-border bg-card p-5">
        <h3 className="mb-3 font-semibold">GMV per day</h3>
        <SalesChart points={o.daily} metric="amount" />
      </section>
    </div>
  )
}
