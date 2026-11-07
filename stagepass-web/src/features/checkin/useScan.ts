import { useCallback, useEffect, useRef, useState } from 'react'
import { isApiError } from '@/lib/api/ApiError'
import { scanTicket } from './api'
import type { ScanResponse } from './types'

const SHOW_MS = 2500

function buzz(valid: boolean) {
  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
    navigator.vibrate(valid ? 80 : [120, 80, 120, 80, 120])
  }
}

/**
 * One scan at a time: while a request is in flight or a result is on screen, new codes are ignored. Results clear
 * themselves after 2.5 s (or on tap). A 404 means the user lost access to this event's door; a gateId error means the
 * organizer removed the gate the doorman picked.
 */
export function useScan(eventId: string, gateId: string | null) {
  const [result, setResult] = useState<ScanResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [accessLost, setAccessLost] = useState(false)
  const [gateGone, setGateGone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastCounts, setLastCounts] = useState<ScanResponse['counts'] | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const dismiss = useCallback(() => {
    if (timer.current) clearTimeout(timer.current)
    timer.current = null
    setResult(null)
  }, [])

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current)
  }, [])

  const scan = useCallback(
    async (input: { token: string } | { code: string }) => {
      if (busy || result) return
      setBusy(true)
      setError(null)
      try {
        const r = await scanTicket({ eventId, gateId, ...input })
        buzz(r.result === 'VALID')
        setResult(r)
        setLastCounts(r.counts)
        timer.current = setTimeout(() => {
          timer.current = null
          setResult(null)
        }, SHOW_MS)
      } catch (e) {
        if (isApiError(e) && e.status === 404) setAccessLost(true)
        else if (isApiError(e) && e.fieldErrors.some((f) => f.field === 'gateId')) setGateGone(true)
        else setError(isApiError(e) ? e.detail : 'Could not reach Stagepass. Check the connection and scan again.')
      } finally {
        setBusy(false)
      }
    },
    [busy, result, eventId, gateId],
  )

  return { scan, result, dismiss, busy, accessLost, gateGone, error, counts: lastCounts }
}
