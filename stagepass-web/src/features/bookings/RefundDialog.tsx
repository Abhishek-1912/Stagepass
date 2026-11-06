import { type ReactNode, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import type { Booking } from '@/features/checkout/types'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { useRequestRefund } from './api'
import { estimateRefund } from './refundEstimate'

export function RefundDialog({ booking, trigger }: { booking: Booking; trigger: ReactNode }) {
  const refund = booking.refund!
  const claimed = new Set(
    refund.history.filter((r) => ['PENDING_APPROVAL', 'REQUESTED', 'PROCESSING'].includes(r.status)).flatMap((r) => r.ticketIds),
  )
  const refundable = booking.tickets.filter((t) => t.status === 'VALID' && !claimed.has(t.id))
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<string[]>(() => refundable.map((t) => t.id))
  const [reason, setReason] = useState('')
  const request = useRequestRefund(booking.id)
  const estimate = estimateRefund(booking, selected, refund.includesFee)

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))

  async function submit() {
    try {
      await request.mutateAsync({ ticketIds: selected, reason: reason.trim() || undefined })
      toast.success(refund.selfServe ? 'Refund started. It usually reaches you in 5–7 working days.' : 'Request sent to the organizer.')
      setOpen(false)
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not request the refund')
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{refund.selfServe ? 'Refund tickets' : 'Request a refund'}</DialogTitle>
          <DialogDescription>{refund.message}</DialogDescription>
        </DialogHeader>
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium">Tickets</legend>
          {refundable.map((t) => (
            <label key={t.id} className="flex items-center gap-3 rounded-xl border border-border px-3 py-2 text-sm">
              <input type="checkbox" checked={selected.includes(t.id)} onChange={() => toggle(t.id)} className="size-4 accent-primary" />
              <span className="font-mono">{t.code}</span>
              <span className="text-muted-foreground">{t.ticketTypeName}</span>
            </label>
          ))}
        </fieldset>
        <label className="block space-y-1.5 text-sm font-medium">
          Reason (optional)
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value.slice(0, 500))}
            className="mt-1.5 min-h-20 w-full rounded-xl border border-input bg-background px-3 py-2.5 text-sm font-normal outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          />
        </label>
        <p className="rounded-xl bg-muted p-3 text-sm" data-testid="refund-estimate">
          {refund.selfServe ? 'You’ll get back' : 'If approved, you’ll get back'} about{' '}
          <strong>{formatPaiseExact(estimate)}</strong>
          {booking.price.feePaise > 0 && (refund.includesFee ? ', convenience fee included.' : '. The convenience fee isn’t refunded.')}
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Back
          </Button>
          <Button disabled={selected.length === 0 || request.isPending} onClick={submit}>
            {request.isPending ? 'Sending…' : refund.selfServe ? 'Refund now' : 'Send request'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
