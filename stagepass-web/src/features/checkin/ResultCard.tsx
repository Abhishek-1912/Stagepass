import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ScanResponse } from './types'

const time = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit' })

const HEADLINE: Record<ScanResponse['result'], string> = {
  VALID: 'Welcome in',
  ALREADY_USED: 'Already used',
  WRONG_GATE: 'Wrong gate',
  WRONG_EVENT: 'Wrong event',
  INVALID: 'Not valid',
}

function firstScanLine(r: ScanResponse) {
  const f = r.firstScan
  if (!f) return null
  const at = new Date(f.at)
  if (f.byYou) {
    return Date.now() - at.getTime() < 2 * 60_000 ? 'Checked in by you just now' : `Checked in by you at ${time.format(at)}`
  }
  return `${f.gateName ? `at ${f.gateName} ` : ''}by ${f.scannerName}, ${time.format(at)}`
}

function detail(r: ScanResponse) {
  switch (r.result) {
    case 'ALREADY_USED':
      return firstScanLine(r)
    case 'WRONG_GATE':
      return r.allowedGates && r.allowedGates.length > 0 ? `Use ${r.allowedGates.join(' or ')}` : r.message
    case 'WRONG_EVENT':
    case 'INVALID':
      return r.message
    default:
      return null
  }
}

/** Full-screen answer for the doorman: green, amber or red, readable at arm's length. Tap to continue. */
export function ResultCard({ result, onDismiss }: { result: ScanResponse; onDismiss: () => void }) {
  const tone = result.result === 'VALID' ? 'bg-emerald-600' : result.result === 'ALREADY_USED' ? 'bg-amber-500' : 'bg-red-600'
  const Icon = result.result === 'VALID' ? CheckCircle2 : result.result === 'ALREADY_USED' ? AlertTriangle : XCircle
  const line = detail(result)
  return (
    <div
      role="status"
      aria-live="assertive"
      onClick={onDismiss}
      className={cn('fixed inset-0 z-50 flex cursor-pointer flex-col items-center justify-center gap-4 p-8 text-center text-white', tone)}
    >
      <Icon className="size-24" aria-hidden />
      <p className="text-5xl font-extrabold tracking-tight">{HEADLINE[result.result]}</p>
      {result.ticket && (
        <div className="space-y-1">
          <p className="text-3xl font-semibold">{result.ticket.holderName}</p>
          <p className="text-xl opacity-90">{result.ticket.ticketType}</p>
          <p className="font-mono text-sm opacity-80">{result.ticket.code}</p>
        </div>
      )}
      {line && <p className="max-w-md text-xl font-medium">{line}</p>}
      <p className="mt-6 text-sm opacity-80">Tap to scan the next ticket</p>
    </div>
  )
}
