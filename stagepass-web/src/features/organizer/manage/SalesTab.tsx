import { IndianRupee, Receipt, RotateCcw, Ticket } from 'lucide-react'
import { useState } from 'react'
import { PageLoader } from '@/components/feedback/PageLoader'
import { formatPaise } from '@/lib/money'
import { useEventStats } from '../dashboard/api'
import { KpiCard } from '../dashboard/KpiCard'
import { kpiMoney } from '../dashboard/kpiMoney'
import { RangePicker } from '../dashboard/RangePicker'
import { SalesChart } from '../dashboard/SalesChart'
import type { Range } from '../dashboard/types'

export function SalesTab({ eventId }: { eventId: string }) {
  const [days, setDays] = useState<Range>(30)
  const stats = useEventStats(eventId, days)
  if (stats.isPending || !stats.data) return <PageLoader label="Loading sales" />
  const s = stats.data

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Tickets sold" value={`${s.ticketsSold} / ${s.capacity}`} hint={s.held > 0 ? `${s.held} in checkout now` : undefined} icon={Ticket} />
        <KpiCard label="Net sales" value={kpiMoney(s.netSalesPaise)} icon={IndianRupee} />
        <KpiCard label="Refunded" value={kpiMoney(s.refundedPaise)} icon={RotateCcw} />
        <KpiCard label="Commission" value={kpiMoney(s.commissionPaise)} icon={Receipt} />
      </div>

      <section className="space-y-4 rounded-3xl border border-border bg-card p-6">
        <div className="flex items-center justify-between gap-3">
          <h3 className="font-sans text-lg font-semibold tracking-normal">Daily sales</h3>
          <RangePicker value={days} onChange={setDays} />
        </div>
        <SalesChart points={s.dailySales} metric="amount" />
      </section>

      {s.byType.length > 0 && (
        <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
          <h3 className="font-sans text-lg font-semibold tracking-normal">By ticket type</h3>
          <ul className="space-y-4">
            {s.byType.map((t) => {
              const pct = t.total === 0 ? 0 : Math.min(100, Math.round((t.sold / t.total) * 100))
              return (
                <li key={t.ticketTypeId} className="space-y-1.5 text-sm">
                  <div className="flex flex-wrap justify-between gap-2">
                    <span className="font-medium">
                      {t.name} <span className="font-normal text-muted-foreground">· {formatPaise(t.pricePaise)}</span>
                    </span>
                    <span className="tabular-nums text-muted-foreground">
                      {t.sold} / {t.total} sold · {kpiMoney(t.netSalesPaise)}
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${t.name} sold`} aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
                    <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      )}
    </div>
  )
}
