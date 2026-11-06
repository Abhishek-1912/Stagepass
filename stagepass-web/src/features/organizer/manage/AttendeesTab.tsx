import { Download } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { downloadFile } from '@/features/bookings/downloads'
import { isApiError } from '@/lib/api/ApiError'
import { type ListQuery, useEventAttendees } from './api'
import { Pager, SearchBox, StatusFilter } from './ListControls'

const STATUSES = [
  { value: '', label: 'Valid & checked in' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'VALID', label: 'Not yet checked in' },
  { value: 'REFUNDED', label: 'Refunded' },
]

const when = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export function AttendeesTab({ eventId }: { eventId: string }) {
  const [query, setQuery] = useState<ListQuery>({ q: '', status: '', page: 0 })
  const attendees = useEventAttendees(eventId, query)

  async function exportCsv() {
    try {
      await downloadFile(`/organizer/events/${eventId}/attendees.csv`, 'attendees.csv')
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not export attendees')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <SearchBox placeholder="Search ticket, booking, name or email" onSearch={(q) => setQuery((s) => ({ ...s, q, page: 0 }))} />
        <StatusFilter value={query.status} options={STATUSES} onChange={(status) => setQuery((s) => ({ ...s, status, page: 0 }))} />
        <Button variant="outline" className="ml-auto rounded-full" onClick={exportCsv}>
          <Download className="size-4" aria-hidden /> Export CSV
        </Button>
      </div>
      {attendees.data && attendees.data.items.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          {query.q || query.status ? 'No attendees match.' : 'No attendees yet.'}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">Ticket</th>
                <th className="px-3 py-2 font-medium">Holder</th>
                <th className="px-3 py-2 font-medium">Contact</th>
                <th className="px-3 py-2 font-medium">Booking</th>
                <th className="px-3 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {attendees.data?.items.map((a) => (
                <tr key={a.ticketId}>
                  <td className="px-3 py-2">
                    <p className="font-mono font-medium">{a.ticketCode}</p>
                    <p className="text-xs text-muted-foreground">{a.ticketType}</p>
                  </td>
                  <td className="px-3 py-2">{a.holderName}</td>
                  <td className="px-3 py-2">
                    <p>{a.attendeeEmail}</p>
                    {a.attendeePhone && <p className="text-xs text-muted-foreground">{a.attendeePhone}</p>}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{a.bookingCode}</td>
                  <td className="px-3 py-2 text-xs">
                    {a.status === 'CHECKED_IN' && a.checkedInAt ? `Checked in ${when.format(new Date(a.checkedInAt))}` : a.status === 'VALID' ? 'Valid' : 'Refunded'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {attendees.data && (
        <Pager page={query.page} totalPages={attendees.data.totalPages} totalItems={attendees.data.totalItems} onPage={(page) => setQuery((s) => ({ ...s, page }))} />
      )}
    </div>
  )
}
