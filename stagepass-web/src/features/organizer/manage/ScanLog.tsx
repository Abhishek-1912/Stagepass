import { useState } from 'react'
import { useScanLog } from '@/features/checkin/api'
import { RESULT_LABEL } from '@/features/checkin/types'
import { cn } from '@/lib/utils'
import { Pager, StatusFilter } from './ListControls'


const FILTERS = [{ value: '', label: 'All scans' }, ...Object.entries(RESULT_LABEL).map(([value, label]) => ({ value, label }))]

const time = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit', second: '2-digit', day: 'numeric', month: 'short' })

/** Every scan at the door, newest first (refreshes every 10 s). */
export function ScanLog({ eventId }: { eventId: string }) {
  const [query, setQuery] = useState({ result: '', page: 0 })
  const log = useScanLog(eventId, query)

  return (
    <section className="space-y-3 rounded-3xl border border-border bg-card p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-sans text-lg font-semibold tracking-normal">Scan log</h3>
        <StatusFilter value={query.result} options={FILTERS} onChange={(result) => setQuery({ result, page: 0 })} />
      </div>
      {log.data && log.data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No scans yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted-foreground">
              <tr>
                <th className="py-2 pr-3 font-medium">Time</th>
                <th className="py-2 pr-3 font-medium">Result</th>
                <th className="py-2 pr-3 font-medium">Ticket</th>
                <th className="py-2 pr-3 font-medium">Gate</th>
                <th className="py-2 font-medium">Scanned by</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {log.data?.items.map((r, i) => (
                <tr key={`${r.at}-${i}`}>
                  <td className="py-2 pr-3 whitespace-nowrap">{time.format(new Date(r.at))}</td>
                  <td className={cn('py-2 pr-3 font-medium', r.result === 'VALID' ? 'text-emerald-700 dark:text-emerald-300' : r.result === 'ALREADY_USED' ? 'text-amber-700 dark:text-amber-300' : 'text-brand-coral')}>
                    {RESULT_LABEL[r.result]}
                  </td>
                  <td className="py-2 pr-3">
                    {r.ticketCode ? <span className="font-mono">{r.ticketCode}</span> : '—'}
                    {r.holderName && <span className="text-muted-foreground"> · {r.holderName}</span>}
                    {r.manual && <span className="ml-1 text-xs text-muted-foreground">(typed)</span>}
                  </td>
                  <td className="py-2 pr-3">{r.gateName ?? '—'}</td>
                  <td className="py-2">{r.scannerName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {log.data && <Pager page={query.page} totalPages={log.data.totalPages} totalItems={log.data.totalItems} onPage={(page) => setQuery((q) => ({ ...q, page }))} />}
    </section>
  )
}
