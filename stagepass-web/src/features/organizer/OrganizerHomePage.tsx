import { CalendarPlus, Clock, Radio, Ticket } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { useOrganizerEvents } from './api'

function Stat({ label, value, icon: Icon }: { label: string; value: number | string; icon: typeof Clock }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        {label}
        <Icon className="size-4" />
      </div>
      <p className="mt-2 font-display text-3xl font-bold">{value}</p>
    </div>
  )
}

/** Overview until the analytics dashboard (Plan 6) lands. */
export function OrganizerHomePage() {
  const { data } = useOrganizerEvents()
  const events = data?.items ?? []
  const count = (s: string) => events.filter((e) => e.status === s).length
  const sold = events.reduce((sum, e) => sum + e.sold, 0)

  return (
    <div className="space-y-8">
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
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Live events" value={count('PUBLISHED')} icon={Radio} />
        <Stat label="In review" value={count('PENDING_REVIEW')} icon={Clock} />
        <Stat label="Drafts" value={count('DRAFT') + count('REJECTED')} icon={CalendarPlus} />
        <Stat label="Tickets sold" value={sold} icon={Ticket} />
      </div>
    </div>
  )
}
