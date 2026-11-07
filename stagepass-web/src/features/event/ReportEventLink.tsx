import { Flag } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '@/features/auth/AuthProvider'
import { ReportEventDialog } from './ReportEventDialog'

/** "Report this event": the dialog when signed in, otherwise a sign-in link that returns here. */
export function ReportEventLink({ eventId }: { eventId: string }) {
  const { session } = useAuth()
  const location = useLocation()
  if (session) return <ReportEventDialog eventId={eventId} />
  return (
    <Link
      to={`/login?next=${encodeURIComponent(location.pathname)}`}
      className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
    >
      <Flag className="size-4" aria-hidden /> Report this event
    </Link>
  )
}
