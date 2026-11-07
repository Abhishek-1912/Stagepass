import { ArrowLeft } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { AdminTable, ErrorState, StatusPill, Td, Th } from './AdminTable'
import { istDate } from './format'
import { ConfirmAction } from './ConfirmAction'
import { useAdminOrganizer, useReinstateOrganizer, useSuspendOrganizer, useUpdateOrganizer } from './api'
import type { AdminOrganizerDetail, OrganizerUpdate } from './types'

function Money({ label, paise, hint }: { label: string; paise: number; hint?: string }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 font-display text-2xl font-bold tabular-nums">{formatPaiseExact(paise)}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

function DetailsForm({ o }: { o: AdminOrganizerDetail }) {
  const update = useUpdateOrganizer()
  const [orgName, setOrgName] = useState(o.orgName)
  const [pan, setPan] = useState(o.pan)
  const [gstin, setGstin] = useState(o.gstin ?? '')
  const [commission, setCommission] = useState(o.commissionBpsOverride == null ? '' : String(o.commissionBpsOverride / 100))
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function submit(e: FormEvent) {
    e.preventDefault()
    const changes: OrganizerUpdate = {}
    if (orgName.trim() !== o.orgName) changes.orgName = orgName.trim()
    if (pan.trim().toUpperCase() !== o.pan) changes.pan = pan.trim().toUpperCase()
    if (gstin.trim().toUpperCase() !== (o.gstin ?? '')) changes.gstin = gstin.trim().toUpperCase()
    const bps = commission.trim() === '' ? null : Math.round(Number(commission) * 100)
    if (commission.trim() !== '' && !Number.isFinite(bps)) {
      setErrors({ commissionBpsOverride: 'Enter a percentage, e.g. 2.5' })
      return
    }
    if (bps !== o.commissionBpsOverride) changes.commissionBpsOverride = bps
    if (Object.keys(changes).length === 0) {
      toast.info?.('Nothing changed')
      return
    }
    setErrors({})
    try {
      await update.mutateAsync({ id: o.id, update: changes })
      toast.success('Organizer details saved')
    } catch (err) {
      if (isApiError(err) && err.fieldErrors.length) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])))
      } else {
        toast.error(isApiError(err) ? err.detail : 'Could not save')
      }
    }
  }

  const field = (id: string, label: string, value: string, set: (v: string) => void, hint?: string) => (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} value={value} onChange={(e) => set(e.target.value)} aria-invalid={!!errors[id]} />
      {errors[id] ? <p className="text-xs text-destructive">{errors[id]}</p> : hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )

  return (
    <form onSubmit={submit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
      <h3 className="font-semibold">Legal and fee details</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        {field('orgName', 'Organization name', orgName, setOrgName)}
        {field('pan', 'PAN', pan, setPan)}
        {field('gstin', 'GSTIN', gstin, setGstin, 'Leave empty if not registered')}
        {field('commissionBpsOverride', 'Commission override (%)', commission, setCommission, 'Empty = platform default')}
      </div>
      <Button type="submit" disabled={update.isPending}>
        {update.isPending ? 'Saving…' : 'Save details'}
      </Button>
    </form>
  )
}

/** One organizer: status, money owed, payouts, and admin edits. */
export function OrganizerDetailPage() {
  const { id } = useParams()
  const { data: o, isPending, isError } = useAdminOrganizer(id)
  const suspend = useSuspendOrganizer()
  const reinstate = useReinstateOrganizer()

  if (isPending) return <PageLoader />
  if (isError || !o) return <ErrorState>We couldn’t load this organizer.</ErrorState>
  const b = o.balances

  return (
    <div className="space-y-6">
      <Link to="/admin/organizers" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> Organizers
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">{o.orgName}</h2>
          <p className="text-sm text-muted-foreground">
            {o.contactEmail} · applied {istDate.format(new Date(o.appliedAt))}
          </p>
          <div className="mt-2">
            <StatusPill tone={o.status === 'APPROVED' ? 'good' : o.status === 'SUSPENDED' ? 'bad' : 'muted'}>{o.status.toLowerCase()}</StatusPill>
          </div>
        </div>
        {o.status === 'APPROVED' && (
          <ConfirmAction
            trigger={<Button variant="destructive">Suspend organizer</Button>}
            title={`Suspend ${o.orgName}?`}
            effect="Their events disappear from Stagepass and stop selling, and they lose the organizer dashboard. Ticket holders can still check in at the door. To stop an event, cancel it."
            confirmLabel="Suspend organizer"
            requireReason
            destructive
            onConfirm={async (reason) => {
              try {
                await suspend.mutateAsync({ id: o.id, reason })
                toast.success(`${o.orgName} suspended`)
              } catch (e) {
                toast.error(isApiError(e) ? e.detail : 'Could not suspend')
                throw e
              }
            }}
          />
        )}
        {o.status === 'SUSPENDED' && (
          <ConfirmAction
            trigger={<Button variant="outline">Reinstate</Button>}
            title={`Reinstate ${o.orgName}?`}
            effect="Their published events show and sell again, and they get their dashboard back."
            confirmLabel="Reinstate organizer"
            onConfirm={async () => {
              try {
                await reinstate.mutateAsync(o.id)
                toast.success(`${o.orgName} reinstated`)
              } catch (e) {
                toast.error(isApiError(e) ? e.detail : 'Could not reinstate')
                throw e
              }
            }}
          />
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Money label="Payable now" paise={b.payablePaise} hint={b.negative ? 'Owes Stagepass — netted from the next payout' : undefined} />
        <Money label="Pending" paise={b.pendingPaise} hint="Payable 3 days after each event ends" />
        <Money label="Paid out" paise={b.paidOutPaise} />
      </div>

      <dl className="grid gap-3 rounded-2xl border border-border bg-card p-5 text-sm sm:grid-cols-3">
        <div><dt className="text-xs text-muted-foreground">Payout account</dt><dd>{o.payoutAccountName ? `${o.payoutAccountName} · ${o.payoutAccountMasked}` : '—'}</dd></div>
        <div><dt className="text-xs text-muted-foreground">IFSC</dt><dd>{o.payoutIfsc ?? '—'}</dd></div>
        <div><dt className="text-xs text-muted-foreground">State</dt><dd>{o.state ?? '—'}</dd></div>
      </dl>

      <DetailsForm key={`${o.id}-${o.pan}-${o.gstin}-${o.orgName}-${o.commissionBpsOverride}`} o={o} />

      <section className="space-y-3">
        <h3 className="font-semibold">Recent payouts</h3>
        {o.payouts.length === 0 ? (
          <p className="text-sm text-muted-foreground">No payouts yet.</p>
        ) : (
          <AdminTable head={<><Th>Paid on</Th><Th>Reference</Th><Th right>Amount</Th><Th>Recorded by</Th></>}>
            {o.payouts.map((p) => (
              <tr key={p.id}>
                <Td>{istDate.format(new Date(p.paidAt))}</Td>
                <Td>{p.reference}</Td>
                <Td right>{formatPaiseExact(p.amountPaise)}</Td>
                <Td className="text-xs">{p.recordedByEmail}</Td>
              </tr>
            ))}
          </AdminTable>
        )}
      </section>
    </div>
  )
}
