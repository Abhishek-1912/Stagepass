import { useState } from 'react'
import { formatPaiseExact } from '@/lib/money'
import { type ListQuery, useEventBookings } from './api'
import { Pager, SearchBox, StatusFilter } from './ListControls'

const STATUSES = [
  { value: '', label: 'All (except expired)' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'PENDING_PAYMENT', label: 'Payment pending' },
  { value: 'PARTIALLY_REFUNDED', label: 'Partly refunded' },
  { value: 'REFUNDED', label: 'Refunded' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'EXPIRED', label: 'Expired' },
]

const LABEL: Record<string, string> = {
  CONFIRMED: 'Confirmed',
  PENDING_PAYMENT: 'Payment pending',
  PARTIALLY_REFUNDED: 'Partly refunded',
  REFUNDED: 'Refunded',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
}

const when = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export function BookingsTab({ eventId }: { eventId: string }) {
  const [query, setQuery] = useState<ListQuery>({ q: '', status: '', page: 0 })
  const bookings = useEventBookings(eventId, query)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <SearchBox placeholder="Search booking code, name or email" onSearch={(q) => setQuery((s) => ({ ...s, q, page: 0 }))} />
        <StatusFilter value={query.status} options={STATUSES} onChange={(status) => setQuery((s) => ({ ...s, status, page: 0 }))} />
      </div>
      {bookings.data && bookings.data.items.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          {query.q || query.status ? 'No bookings match.' : 'No bookings yet.'}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">Booking</th>
                <th className="px-3 py-2 font-medium">Attendee</th>
                <th className="px-3 py-2 text-right font-medium">Tickets</th>
                <th className="px-3 py-2 text-right font-medium">Paid</th>
                <th className="px-3 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {bookings.data?.items.map((b) => (
                <tr key={b.id}>
                  <td className="px-3 py-2">
                    <p className="font-mono font-medium">{b.code}</p>
                    <p className="text-xs text-muted-foreground">{when.format(new Date(b.confirmedAt ?? b.createdAt))}</p>
                  </td>
                  <td className="px-3 py-2">
                    <p>{b.attendeeName}</p>
                    <p className="text-xs text-muted-foreground">{b.attendeeEmail}</p>
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{b.ticketCount}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{formatPaiseExact(b.totalPaise)}</td>
                  <td className="px-3 py-2 text-xs">{LABEL[b.status] ?? b.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {bookings.data && (
        <Pager page={query.page} totalPages={bookings.data.totalPages} totalItems={bookings.data.totalItems} onPage={(page) => setQuery((s) => ({ ...s, page }))} />
      )}
    </div>
  )
}
