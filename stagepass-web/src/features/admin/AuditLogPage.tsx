import { useState } from 'react'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pager } from '@/features/organizer/manage/ListControls'
import { AdminTable, EmptyState, Td, Th } from './AdminTable'
import { useAuditLog, type AuditFilters } from './api'
import { istDateTime } from './format'
import type { AuditRow } from './types'

function show(v: unknown): string {
  if (v === null || v === undefined) return '—'
  return typeof v === 'string' ? v : JSON.stringify(v)
}

/** Key-by-key before → after. */
function Changes({ row }: { row: AuditRow }) {
  const keys = Array.from(new Set([...Object.keys(row.before ?? {}), ...Object.keys(row.after ?? {})]))
  if (keys.length === 0) return <span className="text-muted-foreground">—</span>
  return (
    <ul className="space-y-0.5 text-xs">
      {keys.map((k) => (
        <li key={k} className="break-all">
          <span className="font-medium">{k}</span>:{' '}
          {row.before && k in row.before && <span className="text-muted-foreground line-through">{show(row.before[k])}</span>}
          {row.before && k in row.before && row.after && k in row.after && ' → '}
          {row.after && k in row.after && <span>{show(row.after[k])}</span>}
        </li>
      ))}
    </ul>
  )
}

const FIELDS: { key: keyof AuditFilters; label: string; type?: string }[] = [
  { key: 'actor', label: 'Actor email' },
  { key: 'action', label: 'Action' },
  { key: 'entityType', label: 'Entity type' },
  { key: 'entityId', label: 'Entity id' },
  { key: 'from', label: 'From', type: 'date' },
  { key: 'to', label: 'To', type: 'date' },
]

/** Who changed what, newest first (read-only). */
export function AuditLogPage() {
  const [draft, setDraft] = useState<AuditFilters>({})
  const [filters, setFilters] = useState<AuditFilters>({})
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAuditLog({ ...filters, page })

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Audit log</h2>
        <p className="text-muted-foreground">Approvals, suspensions, refunds, payouts and setting changes.</p>
      </div>
      <form
        className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6"
        onSubmit={(e) => {
          e.preventDefault()
          setFilters(draft)
          setPage(0)
        }}
      >
        {FIELDS.map((f) => (
          <div key={f.key} className="space-y-1">
            <Label htmlFor={`audit-${f.key}`} className="text-xs">{f.label}</Label>
            <Input
              id={`audit-${f.key}`}
              type={f.type ?? 'text'}
              value={(draft[f.key] as string | undefined) ?? ''}
              onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })}
              className="h-9"
            />
          </div>
        ))}
        <button type="submit" className="sr-only">Apply filters</button>
      </form>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load the audit log. Check the filters.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>No entries match.</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>When</Th><Th>Actor</Th><Th>Action</Th><Th>Entity</Th><Th>Changes</Th></>}>
            {data.items.map((a) => (
              <tr key={a.id}>
                <Td className="whitespace-nowrap text-xs">{istDateTime.format(new Date(a.at))}</Td>
                <Td className="text-xs">{a.actorEmail ?? 'System'}</Td>
                <Td className="text-xs font-medium">{a.action}</Td>
                <Td className="text-xs">
                  {a.entityType}
                  {a.entityId && <div className="max-w-40 truncate font-mono text-muted-foreground" title={a.entityId}>{a.entityId}</div>}
                </Td>
                <Td><Changes row={a} /></Td>
              </tr>
            ))}
          </AdminTable>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}
