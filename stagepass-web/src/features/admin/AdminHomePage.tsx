import { CalendarCheck, Store } from 'lucide-react'
import { Link } from 'react-router'
import { useAdminApplications, useAdminEvents } from './api'

function QueueCard({ to, label, count, icon: Icon }: { to: string; label: string; count?: number; icon: typeof Store }) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-card)] transition-all hover:-translate-y-0.5 hover:shadow-[var(--shadow-card-hover)]"
    >
      <div>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-1 font-display text-3xl font-bold">{count ?? '—'}</p>
      </div>
      <span className="grid size-11 place-items-center rounded-xl bg-accent text-accent-foreground">
        <Icon className="size-5" />
      </span>
    </Link>
  )
}

/** Review queues until platform analytics (Plan 8) lands. */
export function AdminHomePage() {
  const applications = useAdminApplications('PENDING')
  const events = useAdminEvents('PENDING_REVIEW')
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Today’s queue</h2>
        <p className="text-muted-foreground">Organizers and events waiting for a decision.</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <QueueCard to="/admin/organizers" label="Organizer applications" count={applications.data?.totalItems} icon={Store} />
        <QueueCard to="/admin/events" label="Events to review" count={events.data?.totalItems} icon={CalendarCheck} />
      </div>
    </div>
  )
}
