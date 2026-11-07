import { Flag } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { isApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/client'

const REASONS = [
  { value: 'SCAM', label: 'Looks like a scam' },
  { value: 'MISLEADING', label: 'Misleading date, venue or price' },
  { value: 'OFFENSIVE', label: 'Offensive or inappropriate' },
  { value: 'DUPLICATE', label: 'Listed more than once' },
  { value: 'OTHER', label: 'Something else' },
] as const

/** Lets a signed-in attendee flag an event for the Stagepass team. */
export function ReportEventDialog({ eventId }: { eventId: string }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<string>('')
  const [details, setDetails] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const needsDetails = reason === 'OTHER'
  const ready = !!reason && (!needsDetails || details.trim().length > 0) && !busy

  async function send() {
    setBusy(true)
    setError(null)
    try {
      const d = details.trim()
      await api(`/events/${eventId}/reports`, { method: 'POST', json: d ? { reason, details: d } : { reason } })
      setDone(true)
    } catch (e) {
      setError(isApiError(e) ? e.detail : 'Could not send the report. Try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o)
        if (!o) {
          setDone(false)
          setReason('')
          setDetails('')
          setError(null)
        }
      }}
    >
      <DialogTrigger asChild>
        <button type="button" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <Flag className="size-4" aria-hidden /> Report this event
        </button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Report this event</DialogTitle>
          <DialogDescription>Tell the Stagepass team what's wrong. The organizer won't see who reported it.</DialogDescription>
        </DialogHeader>
        {done ? (
          <p className="rounded-xl bg-muted/50 p-4 text-sm">Thanks — our team will review it.</p>
        ) : (
          <div className="space-y-4">
            <fieldset className="space-y-2">
              <legend className="sr-only">Reason</legend>
              {REASONS.map((r) => (
                <label key={r.value} className="flex items-center gap-2 text-sm">
                  <input type="radio" name="report-reason" value={r.value} checked={reason === r.value} onChange={() => setReason(r.value)} />
                  {r.label}
                </label>
              ))}
            </fieldset>
            <label className="block space-y-1.5 text-sm font-medium">
              Details {needsDetails ? '' : '(optional)'}
              <textarea
                value={details}
                onChange={(e) => setDetails(e.target.value.slice(0, 1000))}
                className="mt-1.5 min-h-20 w-full rounded-xl border border-input bg-background px-3 py-2.5 text-sm font-normal outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
              />
            </label>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
        )}
        <DialogFooter>
          {done ? (
            <Button onClick={() => setOpen(false)}>Close</Button>
          ) : (
            <>
              <Button variant="outline" onClick={() => setOpen(false)}>
                Back
              </Button>
              <Button disabled={!ready} onClick={send}>
                {busy ? 'Sending…' : 'Send report'}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
