import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'

interface Props {
  trigger: ReactNode
  title: string
  description: string
  confirmLabel: string
  placeholder?: string
  destructive?: boolean
  onConfirm: (reason: string) => Promise<unknown>
}

/** Asks for a required reason (≤ 500 chars) before a reject or cancel action. */
export function ReasonDialog({ trigger, title, description, confirmLabel, placeholder, destructive, onConfirm }: Props) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const trimmed = reason.trim()

  async function confirm() {
    setBusy(true)
    try {
      await onConfirm(trimmed)
      setOpen(false)
      setReason('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <label className="space-y-1.5 text-sm font-medium">
          Reason
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value.slice(0, 500))}
            placeholder={placeholder}
            className="mt-1.5 min-h-28 w-full rounded-xl border border-input bg-background px-3 py-2.5 text-sm font-normal outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          />
          <span className="block text-right text-xs font-normal text-muted-foreground">{reason.length}/500</span>
        </label>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Back
          </Button>
          <Button variant={destructive ? 'destructive' : 'default'} disabled={!trimmed || busy} onClick={confirm}>
            {busy ? 'Saving…' : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
