import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Pager } from '@/features/organizer/manage/ListControls'
import { isApiError } from '@/lib/api/ApiError'
import { cn } from '@/lib/utils'
import { EmptyState } from './AdminTable'
import { ConfirmAction } from './ConfirmAction'
import { useAdminReports, useDecideReport } from './api'
import { istDateTime } from './format'
import type { AdminReportRow, ReportStatus } from './types'

const REASON_LABEL: Record<string, string> = {
  SCAM: 'Looks like a scam',
  MISLEADING: 'Misleading details',
  OFFENSIVE: 'Offensive content',
  DUPLICATE: 'Duplicate listing',
  OTHER: 'Something else',
}

function ReportCard({ r }: { r: AdminReportRow }) {
  const decide = useDecideReport()
  const run = (action: 'resolve' | 'dismiss') => async (note: string) => {
    try {
      await decide.mutateAsync({ id: r.id, action, note })
      toast.success(action === 'resolve' ? 'Report resolved' : 'Report dismissed')
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not save')
      throw e
    }
  }
  return (
    <article className="space-y-3 rounded-2xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to={`/admin/events/${r.eventId}`} className="font-semibold hover:underline">{r.eventTitle}</Link>
          <p className="text-sm text-muted-foreground">
            {r.organizerName} · {r.openReportsForEvent} open {r.openReportsForEvent === 1 ? 'report' : 'reports'} for this event
          </p>
        </div>
        {r.status === 'OPEN' && (
          <div className="flex gap-2">
            <ConfirmAction
              trigger={<Button size="sm" variant="outline">Dismiss</Button>}
              title="Dismiss this report?"
              effect="Use this when nothing is wrong with the event. The reporter can report it again later."
              confirmLabel="Dismiss report"
              requireReason
              onConfirm={run('dismiss')}
            />
            <ConfirmAction
              trigger={<Button size="sm">Resolve</Button>}
              title="Mark this report resolved?"
              effect="Use this after you have acted — for example, the organizer fixed the listing or you cancelled the event."
              confirmLabel="Mark resolved"
              requireReason
              onConfirm={run('resolve')}
            />
          </div>
        )}
      </div>
      <p className="text-sm">
        <span className="font-medium">{REASON_LABEL[r.reason] ?? r.reason}</span>
        <span className="text-muted-foreground"> · from {r.reporterEmail} · {istDateTime.format(new Date(r.createdAt))}</span>
      </p>
      {r.details && <p className="rounded-xl bg-muted/50 p-3 text-sm">{r.details}</p>}
      {r.resolutionNote && <p className="text-sm text-muted-foreground">Note: {r.resolutionNote}</p>}
    </article>
  )
}

/** Events flagged by attendees (spec 18: admin Reports). */
export function ReportsPage() {
  const [status, setStatus] = useState<ReportStatus>('OPEN')
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAdminReports(status, page)
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Reports</h2>
        <p className="text-muted-foreground">Events flagged by attendees. Open the event to cancel it if needed.</p>
      </div>
      <div role="tablist" className="flex gap-1">
        {(['OPEN', 'RESOLVED', 'DISMISSED'] as const).map((s) => (
          <button
            key={s}
            role="tab"
            aria-selected={status === s}
            onClick={() => { setStatus(s); setPage(0) }}
            className={cn(
              'rounded-full px-3.5 py-1.5 text-sm font-medium capitalize',
              status === s ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {s.toLowerCase()}
          </button>
        ))}
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load reports.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>{status === 'OPEN' ? 'No open reports.' : 'Nothing here.'}</EmptyState>
      ) : (
        <div className="space-y-4">
          {data.items.map((r) => (
            <ReportCard key={r.id} r={r} />
          ))}
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </div>
      )}
    </div>
  )
}
