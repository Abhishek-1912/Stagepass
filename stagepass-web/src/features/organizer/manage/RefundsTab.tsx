import { useState } from 'react'
import { toast } from 'sonner'
import { ReasonDialog } from '@/components/feedback/ReasonDialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { type OrganizerRefund, useApproveRefund, useDeclineRefund, useRefundRequests } from './api'

const STATUS: Record<OrganizerRefund['status'], string> = {
  PENDING_APPROVAL: 'Waiting for you',
  REQUESTED: 'Starting',
  PROCESSING: 'Refund on its way',
  PROCESSED: 'Refunded',
  DECLINED: 'Declined',
  FAILED: 'Failed - Stagepass will retry',
}

const when = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

function errorMessage(e: unknown) {
  return isApiError(e) ? e.detail : 'Something went wrong'
}

export function RefundsTab({ eventId }: { eventId: string }) {
  const refunds = useRefundRequests(eventId)
  const approve = useApproveRefund(eventId)
  const decline = useDeclineRefund(eventId)
  const [confirming, setConfirming] = useState<OrganizerRefund | null>(null)

  async function doApprove() {
    if (!confirming) return
    try {
      await approve.mutateAsync(confirming.id)
      toast.success(`Refund of ${formatPaiseExact(confirming.amountPaise)} approved`)
      setConfirming(null)
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  const list = refunds.data ?? []
  if (refunds.data && list.length === 0) {
    return (
      <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        No refund requests. Attendees inside your refund policy are refunded automatically; requests outside it appear here.
      </p>
    )
  }

  return (
    <>
      <ul className="divide-y divide-border rounded-2xl border border-border">
        {list.map((r) => (
          <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 p-4 text-sm">
            <div className="min-w-0 space-y-1">
              <p>
                <span className="font-mono font-medium">{r.bookingCode}</span> · {r.attendeeName}
              </p>
              <p className="text-muted-foreground">
                {r.ticketCount} {r.ticketCount === 1 ? 'ticket' : 'tickets'} · {formatPaiseExact(r.amountPaise)} · {when.format(new Date(r.createdAt))}
              </p>
              {r.reason && <p className="rounded-lg bg-muted px-3 py-2">{r.reason}</p>}
              {r.note && <p className="text-muted-foreground">Your note: {r.note}</p>}
            </div>
            {r.status === 'PENDING_APPROVAL' ? (
              <div className="flex gap-2">
                <ReasonDialog
                  trigger={
                    <Button variant="outline" size="sm" className="rounded-full">
                      Decline
                    </Button>
                  }
                  title="Decline this refund request?"
                  description="The attendee keeps their tickets and gets your note by email."
                  confirmLabel="Decline request"
                  placeholder="e.g. Refunds close a week before the show"
                  onConfirm={async (note) => {
                    try {
                      await decline.mutateAsync({ id: r.id, note })
                      toast.success('Request declined')
                    } catch (e) {
                      toast.error(errorMessage(e))
                    }
                  }}
                />
                <Button size="sm" className="rounded-full" onClick={() => setConfirming(r)}>
                  Approve
                </Button>
              </div>
            ) : (
              <span className="text-xs font-medium">{STATUS[r.status]}</span>
            )}
          </li>
        ))}
      </ul>

      <Dialog open={!!confirming} onOpenChange={(open) => !open && setConfirming(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve this refund?</DialogTitle>
            <DialogDescription>
              {confirming &&
                `${confirming.attendeeName} gets ${formatPaiseExact(confirming.amountPaise)} back for ${confirming.ticketCount} ${
                  confirming.ticketCount === 1 ? 'ticket' : 'tickets'
                }. The tickets stop working and go back on sale. This can't be undone.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(null)}>
              Back
            </Button>
            <Button onClick={doApprove} disabled={approve.isPending}>
              {approve.isPending ? 'Approving…' : 'Approve refund'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
