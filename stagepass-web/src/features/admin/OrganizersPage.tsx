import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Pager, SearchBox, StatusFilter } from '@/features/organizer/manage/ListControls'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { AdminTable, EmptyState, StatusPill, Td, Th } from './AdminTable'
import { useAdminOrganizers } from './api'
import { OrganizerApplicationsPage } from './OrganizerApplicationsPage'

const STATUS_OPTIONS = [
  { value: 'APPROVED', label: 'Approved' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: '', label: 'All' },
]

function OrganizerList() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('APPROVED')
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAdminOrganizers({ q, status, page })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <SearchBox placeholder="Search name, email or slug" onSearch={(v) => { setQ(v); setPage(0) }} />
        <StatusFilter value={status} options={STATUS_OPTIONS} onChange={(v) => { setStatus(v); setPage(0) }} />
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load organizers. Refresh to try again.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>No organizers match.</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>Organizer</Th><Th>Status</Th><Th right>Events</Th><Th right>Payable</Th></>}>
            {data.items.map((o) => (
              <tr key={o.id}>
                <Td>
                  <Link to={`/admin/organizers/${o.id}`} className="font-medium hover:underline">{o.orgName}</Link>
                  <div className="text-xs text-muted-foreground">{o.contactEmail}</div>
                </Td>
                <Td>
                  <StatusPill tone={o.status === 'APPROVED' ? 'good' : o.status === 'SUSPENDED' ? 'bad' : 'muted'}>{o.status.toLowerCase()}</StatusPill>
                </Td>
                <Td right>{o.eventsCount}</Td>
                <Td right className={o.negative ? 'text-destructive' : undefined}>{formatPaiseExact(o.payablePaise)}</Td>
              </tr>
            ))}
          </AdminTable>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}

/** Organizers and their applications, as two tabs. */
export function OrganizersPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'applications' ? 'applications' : 'organizers'
  return (
    <div className="space-y-6">
      <div role="tablist" className="flex gap-1">
        {(['organizers', 'applications'] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setParams(t === 'applications' ? { tab: t } : {})}
            className={cn(
              'rounded-full px-3.5 py-1.5 text-sm font-medium capitalize',
              tab === t ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'applications' ? <OrganizerApplicationsPage /> : <OrganizerList />}
    </div>
  )
}
