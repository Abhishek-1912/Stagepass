import { Html5Qrcode } from 'html5-qrcode'
import { CameraOff } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'

const REPEAT_MS = 3000

/**
 * The phone's back camera reading QR codes. The same code seen again within 3 s is ignored (the camera keeps seeing
 * the ticket while the result shows). Needs HTTPS or localhost; a refused permission shows an explainer.
 */
export function CameraScanner({ onCode, paused }: { onCode: (text: string) => void; paused: boolean }) {
  const id = `qr-${useId().replace(/:/g, '')}`
  const scanner = useRef<Html5Qrcode | null>(null)
  const last = useRef<{ text: string; at: number } | null>(null)
  const latest = useRef(onCode)
  const pausedRef = useRef(paused)
  const [failed, setFailed] = useState<string | null>(null)

  useEffect(() => {
    latest.current = onCode
  }, [onCode])

  useEffect(() => {
    pausedRef.current = paused
  }, [paused])

  useEffect(() => {
    const qr = new Html5Qrcode(id, { verbose: false })
    scanner.current = qr
    const lifecycle = { stopped: false }
    const started = qr.start(
      { facingMode: 'environment' },
      {
        fps: 10,
        qrbox: (w: number, h: number) => {
          const size = Math.floor(Math.min(w, h) * 0.7)
          return { width: size, height: size }
        },
      },
      (text) => {
        const now = Date.now()
        const prev = last.current
        const same = prev !== null && prev.text === text
        // Seeing the same code again keeps it 'recent', so a ticket left in view is never scanned twice.
        if (same) last.current = { text, at: now }
        if (pausedRef.current) return
        if (same && now - prev.at < REPEAT_MS) return
        last.current = { text, at: now }
        latest.current(text)
      },
      () => {},
    )
    started.catch((e: unknown) => {
      if (!lifecycle.stopped) setFailed(e instanceof Error ? e.message : String(e))
    })
    return () => {
      lifecycle.stopped = true
      // start() is asynchronous: stop the camera once it has actually started, even if we left before that.
      void started.then(() => qr.stop()).then(() => qr.clear()).catch(() => {})
    }
  }, [id])

  if (failed) {
    return (
      <div className="flex aspect-square w-full flex-col items-center justify-center gap-3 rounded-3xl bg-white/5 p-6 text-center text-sm text-white/80">
        <CameraOff className="size-10" aria-hidden />
        <p className="font-medium text-white">The camera isn’t available</p>
        <p>Allow camera access for this site in your browser settings, or type the ticket code below.</p>
      </div>
    )
  }
  return <div id={id} className="aspect-square w-full overflow-hidden rounded-3xl bg-black" />
}
