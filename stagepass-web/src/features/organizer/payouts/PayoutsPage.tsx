import { AlertTriangle, Banknote, Clock, Wallet } from 'lucide-react'
import { useState } from 'react'
import { PageLoader } from '@/components/feedback/PageLoader'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { KpiCard } from '../dashboard/KpiCard'
import { kpiMoney } from '../dashboard/kpiMoney'
import { Pager } from '../manage/ListControls'
import { type LedgerLine, useLedger } from './api'

const date = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', year: 'numeric' })

const TYPE: Record<LedgerLine['type'], string> = {
  SALE: 'Ticket sale',
  REFUND: 'Refund',
  COMMISSION: 'Commission',
  PAYOUT: 'Payout',
  ADJUSTMENT: 'Adjustment',
  TDS: 'TDS',
}

/** `/organizer/payouts` — what Stagepass owes the organizer, when it becomes payable, and the full ledger. */
export function PayoutsPage() {
  const [page, setPage] = useState(0)
  const ledger = useLedger(page)

  if (ledger.isPending) return <PageLoader label="Loading payouts" />
  if (ledger.error || !ledger.data) {
    return (
      <p role="status" className="rounded-xl bg-destructive/10 p-4 text-destructive">
        {isApiError(ledger.error) ? ledger.error.detail : 'We couldn’t load your payouts.'}
      </p>
    )
  }
  const { balances: b, entries, payouts } = ledger.data

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold">Payouts</h2>
        <p className="text-muted-foreground">Earnings become payable 3 days after each event ends. Payouts are sent by the Stagepass team.</p>
      </div>

      {b.negative && (
        <div role="alert" className="flex items-start gap-3 rounded-2xl bg-brand-coral/10 p-4 text-sm">
          <AlertTriangle className="size-5 shrink-0 text-brand-coral" aria-hidden />
          <p>
            You owe Stagepass {kpiMoney(-b.payablePaise)} — refunds came after a payout. It will be netted against your next payout.
          </p>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <KpiCard label="Payable now" value={kpiMoney(b.payablePaise)} icon={Wallet} />
        <KpiCard label="Pending" value={kpiMoney(b.pendingPaise)} hint="Events not yet past the 3-day hold" icon={Clock} />
        <KpiCard label="Paid out" value={kpiMoney(b.paidOutPaise)} icon={Banknote} />
      </div>

      {b.events.length > 0 && (
        <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
          <h3 className="font-sans text-lg font-semibold tracking-normal">By event</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-muted-foreground">
                <tr>
                  <th className="py-2 pr-3 font-medium">Event</th>
                  <th className="py-2 pr-3 text-right font-medium">Earned</th>
                  <th className="py-2 pr-3 font-medium">Payable from</th>
                  <th className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {b.events.map((e) => (
                  <tr key={e.eventId}>
                    <td className="py-2 pr-3">{e.title}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatPaiseExact(e.earnedPaise)}</td>
                    <td className="py-2 pr-3">{e.payableFrom ? date.format(new Date(e.payableFrom)) : 'Now (cancelled)'}</td>
                    <td className="py-2">
                      <span className={cn('text-xs font-medium', e.payable ? 'text-emerald-700 dark:text-emerald-300' : 'text-muted-foreground')}>
                        {e.payable ? 'Payable' : 'Pending'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
        <h3 className="font-sans text-lg font-semibold tracking-normal">Payouts received</h3>
        {payouts.length === 0 ? (
          <p className="text-sm text-muted-foreground">No payouts yet.</p>
        ) : (
          <ul className="divide-y divide-border text-sm">
            {payouts.map((p) => (
              <li key={p.id} className="flex flex-wrap justify-between gap-2 py-2">
                <span>
                  {date.format(new Date(p.paidAt))} · <span className="font-mono">{p.reference}</span>
                  {p.note && <span className="text-muted-foreground"> · {p.note}</span>}
                </span>
                <span className="font-medium tabular-nums">{formatPaiseExact(p.amountPaise)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
        <h3 className="font-sans text-lg font-semibold tracking-normal">Ledger</h3>
        {entries.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing here yet. Sales, refunds and payouts appear as they happen.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-muted-foreground">
                <tr>
                  <th className="py-2 pr-3 font-medium">Date</th>
                  <th className="py-2 pr-3 font-medium">Type</th>
                  <th className="py-2 pr-3 font-medium">Event / booking</th>
                  <th className="py-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {entries.items.map((l) => (
                  <tr key={l.id}>
                    <td className="py-2 pr-3 whitespace-nowrap">{date.format(new Date(l.createdAt))}</td>
                    <td className="py-2 pr-3">{TYPE[l.type]}</td>
                    <td className="py-2 pr-3">
                      {l.eventTitle ?? l.note ?? '—'}
                      {l.bookingCode && <span className="ml-2 font-mono text-xs text-muted-foreground">{l.bookingCode}</span>}
                    </td>
                    <td className={cn('py-2 text-right tabular-nums', l.amountPaise < 0 && 'text-muted-foreground')}>
                      {l.amountPaise < 0 ? `−${formatPaiseExact(-l.amountPaise)}` : formatPaiseExact(l.amountPaise)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={entries.totalPages} totalItems={entries.totalItems} onPage={setPage} />
      </section>
    </div>
  )
}
