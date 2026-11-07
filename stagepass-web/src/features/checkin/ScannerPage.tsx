import { ArrowLeft, DoorOpen } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useScannerEvents } from './api'
import { CameraScanner } from './CameraScanner'
import { ResultCard } from './ResultCard'
import type { GateOption, ScannerEvent } from './types'
import { useScan } from './useScan'

function NoAccess() {
  return (
    <div className="mx-auto max-w-md space-y-4 px-4 py-16 text-center">
      <p className="text-lg font-semibold">You no longer have access to this event's door</p>
      <p className="text-sm text-muted-foreground">The organizer may have removed you, or check-in has closed.</p>
      <Button asChild variant="outline" className="rounded-full">
        <Link to="/scan">Back to my events</Link>
      </Button>
    </div>
  )
}

function GatePicker({ event, onPick }: { event: ScannerEvent; onPick: (g: GateOption) => void }) {
  return (
    <div className="mx-auto max-w-md space-y-4 px-4 py-8">
      <Link to="/scan" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden /> My events
      </Link>
      <h1 className="text-2xl font-bold">{event.title}</h1>
      <p className="text-muted-foreground">Which gate are you at?</p>
      <ul className="space-y-3">
        {event.gates.map((g) => (
          <li key={g.id}>
            <button
              type="button"
              onClick={() => onPick(g)}
              className="flex w-full items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4 text-left hover:border-primary/50"
            >
              <span className="flex items-center gap-3 font-semibold">
                <DoorOpen className="size-5 text-primary" aria-hidden /> {g.name}
              </span>
              <span className="text-xs text-muted-foreground">{g.ticketTypeNames.length === 0 ? 'All tickets' : `${g.ticketTypeNames.join(', ')} only`}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Scanner({ event, gate, onChangeGate }: { event: ScannerEvent; gate: GateOption | null; onChangeGate: () => void }) {
  const { scan, result, dismiss, busy, accessLost, error, counts } = useScan(event.id, gate?.id ?? null)
  const [code, setCode] = useState('')

  if (accessLost) return <NoAccess />

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!code.trim()) return
    void scan({ code: code.trim() })
    setCode('')
  }

  return (
    <div className="min-h-dvh bg-[#0b0c10] text-white">
      <div className="mx-auto max-w-md space-y-5 px-4 py-5">
        <header className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <Link to="/scan" className="inline-flex items-center gap-1 text-xs text-white/60 hover:text-white">
              <ArrowLeft className="size-3.5" aria-hidden /> My events
            </Link>
            <h1 className="truncate text-lg font-bold">{event.title}</h1>
            {gate && (
              <p className="text-sm text-white/70">
                {gate.name}{' '}
                <button type="button" onClick={onChangeGate} className="underline">
                  Change
                </button>
              </p>
            )}
          </div>
          {counts && (
            <p className="rounded-full bg-white/10 px-3 py-1 text-sm font-semibold tabular-nums" aria-label="Checked in">
              {counts.checkedIn} / {counts.total}
            </p>
          )}
        </header>

        <CameraScanner paused={busy || !!result} onCode={(token) => void scan({ token })} />

        {error && (
          <p role="alert" className="rounded-xl bg-red-500/20 p-3 text-sm">
            {error}
          </p>
        )}

        <form onSubmit={submit} className="space-y-2">
          <label htmlFor="ticket-code" className="text-sm text-white/70">
            Ticket code
          </label>
          <div className="flex gap-2">
            <Input
              id="ticket-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="SP-XXXX-XXXX"
              autoCapitalize="characters"
              autoComplete="off"
              className="border-white/20 bg-white/5 font-mono text-white placeholder:text-white/40"
            />
            <Button type="submit" className="rounded-full" disabled={busy || !code.trim()}>
              Check
            </Button>
          </div>
        </form>
      </div>
      {result && <ResultCard result={result} onDismiss={dismiss} />}
    </div>
  )
}

/** `/scan/:eventId` — pick a gate (when the event has gates), then scan or type ticket codes. */
export function ScannerPage() {
  const { eventId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const events = useScannerEvents()

  if (events.isPending) return <PageLoader label="Opening the scanner" />
  const event = events.data?.find((e) => e.id === eventId)
  if (!event) return <NoAccess />

  const gateId = params.get('gate')
  const gate = event.gates.find((g) => g.id === gateId) ?? null
  if (event.gates.length > 0 && !gate) {
    return <GatePicker event={event} onPick={(g) => setParams({ gate: g.id }, { replace: true })} />
  }
  return <Scanner key={gate?.id ?? 'none'} event={event} gate={gate} onChangeGate={() => setParams({}, { replace: true })} />
}
