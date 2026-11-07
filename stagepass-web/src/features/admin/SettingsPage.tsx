import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact, paiseToRupeesInput, rupeesToPaise } from '@/lib/money'
import { ErrorState } from './AdminTable'
import { useFeeSettings, useSaveFeeSettings } from './api'
import { feePreview } from './feeMath'
import type { FeeSettings } from './types'

type Draft = { fee: string; flat: string; gst: string; commission: string; refundFee: boolean }

const toPct = (bps: number) => String(bps / 100)

/** Percent text → basis points, or an error message. */
function bps(text: string, max: number): number | string {
  const n = Number(text.trim())
  if (text.trim() === '' || !Number.isFinite(n) || n < 0 || n > max) return `Enter a percentage between 0 and ${max}`
  return Math.round(n * 100)
}

function parse(d: Draft): { value?: FeeSettings; errors: Record<string, string> } {
  const errors: Record<string, string> = {}
  const fee = bps(d.fee, 20)
  const gst = bps(d.gst, 28)
  const commission = bps(d.commission, 50)
  let flat: number | string
  try {
    flat = rupeesToPaise(d.flat)
    if (flat > 100000) flat = 'Enter at most ₹1,000 per ticket'
  } catch (e) {
    flat = (e as Error).message
  }
  if (typeof fee === 'string') errors.fee = fee
  if (typeof gst === 'string') errors.gst = gst
  if (typeof commission === 'string') errors.commission = commission
  if (typeof flat === 'string') errors.flat = flat
  if (Object.keys(errors).length) return { errors }
  return {
    errors,
    value: {
      convenienceFeeBps: fee as number,
      convenienceFeeFlatPaise: flat as number,
      gstBps: gst as number,
      commissionBps: commission as number,
      refundFeeOnAttendeeRequest: d.refundFee,
    },
  }
}

function FeeForm({ initial }: { initial: FeeSettings }) {
  const save = useSaveFeeSettings()
  const [d, setD] = useState<Draft>({
    fee: toPct(initial.convenienceFeeBps),
    flat: paiseToRupeesInput(initial.convenienceFeeFlatPaise),
    gst: toPct(initial.gstBps),
    commission: toPct(initial.commissionBps),
    refundFee: initial.refundFeeOnAttendeeRequest,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const parsed = parse(d)
  const example = parsed.value ? feePreview(parsed.value, 100000, 2) : null

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!parsed.value) {
      setErrors(parsed.errors)
      return
    }
    setErrors({})
    try {
      await save.mutateAsync(parsed.value)
      toast.success('Fees saved. New bookings use them from now on.')
    } catch (err) {
      toast.error(isApiError(err) ? err.detail : 'Could not save fees')
    }
  }

  const field = (key: keyof Omit<Draft, 'refundFee'>, label: string, hint: string) => (
    <div className="space-y-1.5">
      <Label htmlFor={`fee-${key}`}>{label}</Label>
      <Input id={`fee-${key}`} inputMode="decimal" value={d[key]} onChange={(e) => setD({ ...d, [key]: e.target.value })} aria-invalid={!!errors[key]} />
      {errors[key] ? <p className="text-xs text-destructive">{errors[key]}</p> : <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )

  return (
    <form onSubmit={submit} className="space-y-6">
      <div className="grid gap-4 rounded-2xl border border-border bg-card p-5 sm:grid-cols-2">
        {field('fee', 'Convenience fee (%)', 'Of the ticket subtotal, paid by the attendee')}
        {field('flat', 'Flat fee per ticket (₹)', 'Added for every ticket')}
        {field('gst', 'GST on the fee (%)', 'Charged on the convenience fee only')}
        {field('commission', 'Organizer commission (%)', 'Taken from the organizer’s payout; organizers can have their own')}
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" checked={d.refundFee} onChange={(e) => setD({ ...d, refundFee: e.target.checked })} />
          Refund the convenience fee when an attendee asks for a refund
        </label>
      </div>
      <div className="rounded-2xl bg-muted/50 p-4 text-sm">
        {example ? (
          <>
            On a ₹1,000 × 2 order the attendee pays <strong>{formatPaiseExact(example.total)}</strong> — fee{' '}
            {formatPaiseExact(example.fee)} + GST {formatPaiseExact(example.gst)}.
          </>
        ) : (
          'Fix the highlighted values to see an example.'
        )}
      </div>
      <p className="text-xs text-muted-foreground">Changes apply to new quotes and bookings only. Existing bookings keep what they paid.</p>
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? 'Saving…' : 'Save fees'}
      </Button>
    </form>
  )
}

/** Platform fee settings (spec 8.2). */
export function SettingsPage() {
  const { data, isPending, isError } = useFeeSettings()
  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Settings</h2>
        <p className="text-muted-foreground">Fees charged on every paid booking.</p>
      </div>
      {isPending ? <PageLoader /> : isError || !data ? <ErrorState>We couldn’t load the fee settings.</ErrorState> : <FeeForm initial={data} />}
    </div>
  )
}
