import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'

interface Props {
  trigger: ReactNode
  title: string
  /** What will happen, in plain words. */
  effect: string
  confirmLabel: string
  /** Ask for a reason of 3–500 characters before the action can run. */
  requireReason?: boolean
  destructive?: boolean
  /** Extra controls shown above the reason (e.g. an include-fee checkbox). */
  children?: ReactNode
  onConfirm: (reason: string) => Promise<unknown>
}

/** Confirms an admin action that is hard to undo; the button stays disabled while it runs. */
export function ConfirmAction({ trigger, title, effect, confirmLabel, requireReason, destructive, children, onConfirm }: Props) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const trimmed = reason.trim()
  const ready = !busy && (!requireReason || trimmed.length >= 3)

  async function confirm() {
    if (!ready) return
    setBusy(true)
    try {
      await onConfirm(requireReason ? trimmed : '')
      setOpen(false)
      setReason('')
    } catch {
      // The caller shows the error; keep the dialog open so the admin can retry.
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !busy && setOpen(o)}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{effect}</DialogDescription>
        </DialogHeader>
        {children}
        {requireReason && (
          <label className="space-y-1.5 text-sm font-medium">
            Reason
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value.slice(0, 500))}
              className="mt-1.5 min-h-24 w-full rounded-xl border border-input bg-background px-3 py-2.5 text-sm font-normal outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
            />
            <span className="block text-right text-xs font-normal text-muted-foreground">{reason.length}/500</span>
          </label>
        )}
        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={() => setOpen(false)}>
            Back
          </Button>
          <Button variant={destructive ? 'destructive' : 'default'} disabled={!ready} onClick={confirm}>
            {busy ? 'Saving…' : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
