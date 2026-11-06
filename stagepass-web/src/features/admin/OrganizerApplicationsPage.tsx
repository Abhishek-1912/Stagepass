import { Check, X } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ReasonDialog } from '@/components/feedback/ReasonDialog'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import type { OrganizerApplication, OrganizerStatus } from '@/lib/api/types'
import { cn } from '@/lib/utils'
import { useAdminApplications, useApproveApplication, useRejectApplication } from './api'

const TABS: { status: OrganizerStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pending' },
  { status: 'APPROVED', label: 'Approved' },
  { status: 'REJECTED', label: 'Rejected' },
]

const dateFmt = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeZone: 'Asia/Kolkata' })

function Detail({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{value || '—'}</dd>
    </div>
  )
}

function ApplicationCard({ a }: { a: OrganizerApplication }) {
  const approve = useApproveApplication()
  const reject = useRejectApplication()

  async function onApprove() {
    try {
      await approve.mutateAsync(a.id)
      toast.success(`${a.orgName} approved`)
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not approve')
    }
  }

  return (
    <article className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
      <div className="flex flex-wrap items-start gap-4">
        {a.logoUrl ? (
          <img src={a.logoUrl} alt="" className="size-12 rounded-xl object-cover" />
        ) : (
          <span className="bg-gradient-brand grid size-12 place-items-center rounded-xl font-bold text-white">{a.orgName[0]}</span>
        )}
        <div className="min-w-0 flex-1">
          <h3 className="font-sans text-base font-semibold tracking-normal">{a.orgName}</h3>
          <p className="text-sm text-muted-foreground">Applied {dateFmt.format(new Date(a.appliedAt))}</p>
          {a.bio && <p className="mt-2 text-sm">{a.bio}</p>}
        </div>
        {a.status === 'PENDING' && (
          <div className="flex gap-2">
            <ReasonDialog
              title={`Reject ${a.orgName}?`}
              description="The applicant sees this reason and can fix their details and reapply."
              confirmLabel="Reject application"
              destructive
              trigger={
                <Button variant="outline" aria-label={`Reject ${a.orgName}`}>
                  <X /> Reject
                </Button>
              }
              onConfirm={async (reason) => {
                try {
                  await reject.mutateAsync({ id: a.id, reason })
                  toast.success(`${a.orgName} rejected`)
                } catch (e) {
                  toast.error(isApiError(e) ? e.detail : 'Could not reject')
                  throw e
                }
              }}
            />
            <Button onClick={onApprove} disabled={approve.isPending} aria-label={`Approve ${a.orgName}`}>
              <Check /> Approve
            </Button>
          </div>
        )}
      </div>
      <dl className="grid gap-3 border-t border-border pt-4 sm:grid-cols-3 lg:grid-cols-4">
        <Detail label="Contact" value={a.contactEmail} />
        <Detail label="Phone" value={a.contactPhone} />
        <Detail label="Website" value={a.website} />
        <Detail label="State" value={a.state} />
        <Detail label="PAN" value={a.pan} />
        <Detail label="GSTIN" value={a.gstin} />
        <Detail label="Payout account" value={a.payoutAccountName ? `${a.payoutAccountName} · ${a.payoutAccountMasked}` : null} />
        <Detail label="IFSC" value={a.payoutIfsc} />
      </dl>
      {a.rejectionReason && <p className="rounded-xl bg-destructive/5 p-3 text-sm text-destructive">Rejected: {a.rejectionReason}</p>}
    </article>
  )
}

export function OrganizerApplicationsPage() {
  const [status, setStatus] = useState<OrganizerStatus>('PENDING')
  const { data, isPending } = useAdminApplications(status)

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Organizers</h2>
        <p className="text-muted-foreground">Check identity and payout details before approving.</p>
      </div>
      <div role="tablist" className="flex gap-1">
        {TABS.map((t) => (
          <button
            key={t.status}
            role="tab"
            aria-selected={status === t.status}
            onClick={() => setStatus(t.status)}
            className={cn(
              'rounded-full px-3.5 py-1.5 text-sm font-medium',
              status === t.status ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      {isPending ? (
        <PageLoader />
      ) : data?.items.length ? (
        <div className="space-y-4">
          {data.items.map((a) => (
            <ApplicationCard key={a.id} a={a} />
          ))}
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
          Nothing here.
        </div>
      )}
    </div>
  )
}
