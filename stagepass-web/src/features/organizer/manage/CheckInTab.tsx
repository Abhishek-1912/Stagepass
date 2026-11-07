import { ScanLine } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { useCheckInStats } from '@/features/checkin/api'
import { useEventStats } from '../dashboard/api'
import { GatesEditor } from './GatesEditor'
import { ScanLog } from './ScanLog'

const time = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit' })

function Bars({ title, rows }: { title: string; rows: { label: string; value: number }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value))
  if (rows.length === 0) return null
  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium">{title}</h4>
      <ul className="space-y-2">
        {rows.map((r) => (
          <li key={r.label} className="space-y-1 text-sm">
            <span>
              {r.label} · {r.value}
            </span>
            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary" style={{ width: `${(r.value / max) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Live door view for the organizer: counts (refreshing every 10 s), gates, and the scan log. */
export function CheckInTab({ eventId }: { eventId: string }) {
  const stats = useCheckInStats(eventId, { poll: true })
  const eventStats = useEventStats(eventId, 30)
  const s = stats.data
  const checkedIn = s?.checkedIn ?? 0
  const total = s?.total ?? 0
  const pct = total === 0 ? 0 : Math.round((checkedIn / total) * 100)
  const ticketTypes = (eventStats.data?.byType ?? []).map((t) => ({ id: t.ticketTypeId, name: t.name }))

  return (
    <div className="space-y-6">
      <section className="space-y-4 rounded-3xl border border-border bg-card p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-2xl font-bold tabular-nums">
              {checkedIn} of {total} checked in
            </p>
            {s?.lastScanAt && <p className="text-xs text-muted-foreground">Last scan {time.format(new Date(s.lastScanAt))} · updates every 10 seconds</p>}
          </div>
          <Button asChild className="rounded-full">
            <Link to={`/scan/${eventId}`}>
              <ScanLine className="size-4" aria-hidden /> Open scanner
            </Link>
          </Button>
        </div>
        <div className="h-3 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Checked in" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
        </div>
        <div className="grid gap-6 sm:grid-cols-2">
          <Bars title="By gate" rows={(s?.byGate ?? []).map((g) => ({ label: g.name, value: g.checkedIn }))} />
          <Bars title="By door staff" rows={(s?.byScanner ?? []).map((x) => ({ label: x.name, value: x.checkedIn }))} />
        </div>
      </section>

      <GatesEditor eventId={eventId} ticketTypes={ticketTypes} />
      <ScanLog eventId={eventId} />
    </div>
  )
}
