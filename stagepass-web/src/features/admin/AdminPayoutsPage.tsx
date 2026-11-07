import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pager, SearchBox } from '@/features/organizer/manage/ListControls'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact, paiseToRupeesInput, rupeesToPaise } from '@/lib/money'
import { AdminTable, EmptyState, StatusPill, Td, Th } from './AdminTable'
import { useAdminPayouts, useRecordPayout } from './api'
import { istDate, todayIst } from './format'
import type { OrganizerPayoutSummary } from './types'

function RecordPayoutDialog({ s }: { s: OrganizerPayoutSummary }) {
  const record = useRecordPayout()
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState(paiseToRupeesInput(Math.max(s.payablePaise, 0)))
  const [reference, setReference] = useState('')
  const [paidOn, setPaidOn] = useState(todayIst())
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function submit(e: FormEvent) {
    e.preventDefault()
    let amountPaise: number
    try {
      amountPaise = rupeesToPaise(amount)
    } catch (err) {
      setErrors({ amountPaise: (err as Error).message })
      return
    }
    setErrors({})
    try {
      await record.mutateAsync({
        organizerId: s.organizerId,
        input: { amountPaise, reference: reference.trim(), paidOn, ...(note.trim() ? { note: note.trim() } : {}) },
      })
      toast.success(`Payout of ${formatPaiseExact(amountPaise)} recorded for ${s.orgName}`)
      setOpen(false)
      setReference('')
      setNote('')
    } catch (err) {
      if (isApiError(err) && err.fieldErrors.length) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])))
      } else {
        toast.error(isApiError(err) ? err.detail : 'Could not record the payout')
      }
    }
  }

  const err = (k: string) => errors[k] && <p className="text-xs text-destructive">{errors[k]}</p>

  return (
    <Dialog open={open} onOpenChange={(o) => !record.isPending && setOpen(o)}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={s.payablePaise <= 0}>
          Record payout
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Record a payout to {s.orgName}</DialogTitle>
            <DialogDescription>
              Record a bank transfer you have already made. Payable now: {formatPaiseExact(s.payablePaise)}. The organizer gets an email.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="payout-amount">Amount (₹)</Label>
            <Input id="payout-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
            {err('amountPaise')}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="payout-reference">Bank reference (UTR)</Label>
            <Input id="payout-reference" value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} />
            {err('reference')}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="payout-date">Paid on</Label>
            <Input id="payout-date" type="date" value={paidOn} max={todayIst()} onChange={(e) => setPaidOn(e.target.value)} />
            {err('paidOn')}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="payout-note">Note (optional)</Label>
            <Input id="payout-note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={record.isPending}>
              Back
            </Button>
            <Button type="submit" disabled={record.isPending || reference.trim().length < 3 || !paidOn}>
              {record.isPending ? 'Saving…' : 'Save payout'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** What Stagepass owes each organizer, and recording payouts (spec 8.4a). */
export function AdminPayoutsPage() {
  const [q, setQ] = useState('')
  const [onlyPayable, setOnlyPayable] = useState(true)
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAdminPayouts({ q, onlyPayable, page })

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Payouts</h2>
        <p className="text-muted-foreground">Earnings become payable 3 days after an event ends, or at once if it was cancelled.</p>
      </div>
      <div className="flex flex-wrap items-center gap-4">
        <SearchBox placeholder="Search organizers" onSearch={(v) => { setQ(v); setPage(0) }} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={onlyPayable} onChange={(e) => { setOnlyPayable(e.target.checked); setPage(0) }} />
          Only payable
        </label>
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load payouts. Refresh to try again.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>{onlyPayable ? 'Nobody is due a payout right now.' : 'No organizers match.'}</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>Organizer</Th><Th right>Payable</Th><Th right>Pending</Th><Th right>Paid out</Th><Th>Last payout</Th><Th right> </Th></>}>
            {data.items.map((s) => (
              <tr key={s.organizerId}>
                <Td>
                  <div className="font-medium">{s.orgName}</div>
                  {s.status === 'SUSPENDED' && <StatusPill tone="bad">suspended</StatusPill>}
                </Td>
                <Td right className={s.negative ? 'text-destructive' : undefined}>
                  {formatPaiseExact(s.payablePaise)}
                  {s.negative && <div className="text-xs">owes Stagepass</div>}
                </Td>
                <Td right>{formatPaiseExact(s.pendingPaise)}</Td>
                <Td right>{formatPaiseExact(s.paidOutPaise)}</Td>
                <Td className="text-xs">{s.lastPayoutAt ? istDate.format(new Date(s.lastPayoutAt)) : '—'}</Td>
                <Td right>
                  <RecordPayoutDialog s={s} />
                </Td>
              </tr>
            ))}
          </AdminTable>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}
