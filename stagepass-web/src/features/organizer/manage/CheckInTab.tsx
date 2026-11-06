import { ScanLine } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useEventStats } from '../dashboard/api'

/** Check-in progress; the scanner (with door staff and gates) arrives in Plan 7. */
export function CheckInTab({ eventId }: { eventId: string }) {
  const stats = useEventStats(eventId, 30)
  const sold = stats.data?.ticketsSold ?? 0
  const checkedIn = stats.data?.checkedIn ?? 0
  const pct = sold === 0 ? 0 : Math.round((checkedIn / sold) * 100)

  return (
    <section className="space-y-4 rounded-3xl border border-border bg-card p-6">
      <p className="text-2xl font-bold tabular-nums">
        {checkedIn} of {sold} checked in
      </p>
      <div className="h-3 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Checked in" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button className="rounded-full" disabled>
          <ScanLine className="size-4" aria-hidden /> Open scanner
        </Button>
        <p className="text-sm text-muted-foreground">Coming with the scanner: door staff, gates and live check-in.</p>
      </div>
    </section>
  )
}
