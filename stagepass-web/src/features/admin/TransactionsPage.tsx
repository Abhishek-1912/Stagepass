import { Download } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { downloadFile } from '@/features/bookings/downloads'
import { Pager, SearchBox, StatusFilter } from '@/features/organizer/manage/ListControls'
import { isApiError } from '@/lib/api/ApiError'
import { formatPaiseExact } from '@/lib/money'
import { cn } from '@/lib/utils'
import { AdminTable, EmptyState, StatusPill, Td, Th } from './AdminTable'
import { ConfirmAction } from './ConfirmAction'
import { query, useAdminDisputes, useAdminPayments, useAdminRefund, useAdminRefunds, useRetryRefund } from './api'
import { istDateTime } from './format'
import type { AdminPaymentRow } from './types'

type Tab = 'payments' | 'refunds' | 'disputes'

const PAYMENT_STATUSES = [
  { value: '', label: 'Any status' },
  { value: 'CAPTURED', label: 'Captured' },
  { value: 'CREATED', label: 'Created' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
]
const REFUND_STATUSES = [
  { value: '', label: 'Any status' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'PROCESSED', label: 'Processed' },
  { value: 'PENDING_APPROVAL', label: 'Waiting for organizer' },
  { value: 'DECLINED', label: 'Declined' },
]

function when(iso: string) {
  return istDateTime.format(new Date(iso))
}

function RefundButton({ p }: { p: AdminPaymentRow }) {
  const refund = useAdminRefund()
  const [includeFee, setIncludeFee] = useState(true)
  return (
    <ConfirmAction
      trigger={<Button size="sm" variant="outline">Refund</Button>}
      title={`Refund booking ${p.bookingCode}?`}
      effect="Every ticket on this booking that hasn't been used or refunded is refunded, whatever the event's refund policy. The attendee gets an email."
      confirmLabel="Refund booking"
      requireReason
      destructive
      onConfirm={async (reason) => {
        try {
          await refund.mutateAsync({ bookingId: p.bookingId, includeFee, reason })
          toast.success(`Refund started for ${p.bookingCode}`)
        } catch (e) {
          toast.error(isApiError(e) ? e.detail : 'Could not refund')
          throw e
        }
      }}
    >
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={includeFee} onChange={(e) => setIncludeFee(e.target.checked)} />
        Also refund the convenience fee and its GST
      </label>
    </ConfirmAction>
  )
}

function Payments() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [exporting, setExporting] = useState(false)
  const { data, isPending, isError } = useAdminPayments({ q, status, from, to, page })

  async function exportCsv() {
    setExporting(true)
    try {
      await downloadFile(`/admin/payments/export${query({ from, to, status })}`, 'stagepass-payments.csv')
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not export')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <SearchBox placeholder="Booking code, email or Razorpay id" onSearch={(v) => { setQ(v); setPage(0) }} />
        <StatusFilter value={status} options={PAYMENT_STATUSES} onChange={(v) => { setStatus(v); setPage(0) }} />
        <div className="space-y-1">
          <Label htmlFor="tx-from" className="text-xs">From</Label>
          <Input id="tx-from" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} className="h-9" />
        </div>
        <div className="space-y-1">
          <Label htmlFor="tx-to" className="text-xs">To</Label>
          <Input id="tx-to" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} className="h-9" />
        </div>
        <Button variant="outline" onClick={exportCsv} disabled={!from || !to || exporting} title="Pick both dates (up to 366 days)">
          <Download /> {exporting ? 'Exporting…' : 'Export CSV'}
        </Button>
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load payments. Check the dates and try again.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>No payments match.</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>Booking</Th><Th>Attendee</Th><Th right>Amount</Th><Th>Status</Th><Th>Razorpay</Th><Th>When</Th><Th right> </Th></>}>
            {data.items.map((p) => (
              <tr key={p.id}>
                <Td>
                  <div className="font-medium">{p.bookingCode}</div>
                  <div className="text-xs text-muted-foreground">{p.eventTitle}</div>
                </Td>
                <Td className="text-xs">{p.attendeeEmail}</Td>
                <Td right>{formatPaiseExact(p.amountPaise)}</Td>
                <Td>
                  <StatusPill tone={p.status === 'CAPTURED' ? 'good' : p.status === 'FAILED' ? 'bad' : 'muted'}>{p.status.toLowerCase()}</StatusPill>
                </Td>
                <Td className="font-mono text-xs">{p.razorpayPaymentId ?? '—'}</Td>
                <Td className="whitespace-nowrap text-xs">{when(p.createdAt)}</Td>
                <Td right>{p.status === 'CAPTURED' && <RefundButton p={p} />}</Td>
              </tr>
            ))}
          </AdminTable>
          <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
        </>
      )}
    </div>
  )
}

function Refunds() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAdminRefunds({ q, status, page })
  const retry = useRetryRefund()

  async function onRetry(id: string) {
    try {
      await retry.mutateAsync(id)
      toast.success('Refund sent to Razorpay again')
    } catch (e) {
      toast.error(isApiError(e) ? e.detail : 'Could not retry')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <SearchBox placeholder="Booking code, email or refund id" onSearch={(v) => { setQ(v); setPage(0) }} />
        <StatusFilter value={status} options={REFUND_STATUSES} onChange={(v) => { setStatus(v); setPage(0) }} />
      </div>
      {isPending ? (
        <PageLoader />
      ) : isError ? (
        <EmptyState>We couldn’t load refunds.</EmptyState>
      ) : !data?.items.length ? (
        <EmptyState>No refunds match.</EmptyState>
      ) : (
        <>
          <AdminTable head={<><Th>Booking</Th><Th>Kind</Th><Th right>Amount</Th><Th>Status</Th><Th>When</Th><Th right> </Th></>}>
            {data.items.map((r) => (
              <tr key={r.id}>
                <Td>
                  <div className="font-medium">{r.bookingCode}</div>
                  <div className="text-xs text-muted-foreground">{r.eventTitle}</div>
                </Td>
                <Td className="text-xs">{r.kind.toLowerCase().replace('_', ' ')}</Td>
                <Td right>{formatPaiseExact(r.amountPaise)}</Td>
                <Td>
                  <StatusPill tone={r.status === 'PROCESSED' ? 'good' : r.status === 'FAILED' ? 'bad' : 'warn'}>{r.status.toLowerCase().replace('_', ' ')}</StatusPill>
                  {r.failureReason && <div className="mt-1 max-w-56 text-xs text-muted-foreground">{r.failureReason}</div>}
                  {r.attempts > 0 && <div className="text-xs text-muted-foreground">{r.attempts} attempts</div>}
                </Td>
                <Td className="whitespace-nowrap text-xs">{when(r.createdAt)}</Td>
                <Td right>
                  {r.status === 'FAILED' && r.needsRetry && (
                    <Button size="sm" variant="outline" onClick={() => onRetry(r.id)} disabled={retry.isPending}>
                      Retry
                    </Button>
                  )}
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

function Disputes() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useAdminDisputes({ page })
  if (isPending) return <PageLoader />
  if (isError) return <EmptyState>We couldn’t load disputes.</EmptyState>
  if (!data?.items.length) return <EmptyState>No disputes. Razorpay sends them here when a customer disputes a payment.</EmptyState>
  return (
    <>
      <AdminTable head={<><Th>Booking</Th><Th>Dispute</Th><Th right>Amount</Th><Th>Reason</Th><Th>Status</Th><Th>When</Th></>}>
        {data.items.map((d) => (
          <tr key={d.id}>
            <Td>
              <div className="font-medium">{d.bookingCode}</div>
              <div className="text-xs text-muted-foreground">{d.eventTitle}</div>
            </Td>
            <Td className="font-mono text-xs">{d.razorpayDisputeId}</Td>
            <Td right>{formatPaiseExact(d.amountPaise)}</Td>
            <Td className="text-xs">{d.reason ?? '—'}</Td>
            <Td><StatusPill tone="warn">{d.status}</StatusPill></Td>
            <Td className="whitespace-nowrap text-xs">{when(d.createdAt)}</Td>
          </tr>
        ))}
      </AdminTable>
      <Pager page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onPage={setPage} />
    </>
  )
}

/** Payments, refunds and disputes, with the reconciliation CSV. */
export function TransactionsPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('tab')
  const tab: Tab = raw === 'refunds' || raw === 'disputes' ? raw : 'payments'
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Transactions</h2>
        <p className="text-muted-foreground">Times are in IST.</p>
      </div>
      <div role="tablist" className="flex gap-1">
        {(['payments', 'refunds', 'disputes'] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setParams(t === 'payments' ? {} : { tab: t })}
            className={cn(
              'rounded-full px-3.5 py-1.5 text-sm font-medium capitalize',
              tab === t ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'payments' ? <Payments /> : tab === 'refunds' ? <Refunds /> : <Disputes />}
    </div>
  )
}
