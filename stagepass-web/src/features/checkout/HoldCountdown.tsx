import { Timer } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

function remainingMs(expiresAt: string) {
  return Math.max(0, new Date(expiresAt).getTime() - Date.now())
}

/** "Tickets held for 9:42". Calls {@code onExpire} once when it reaches zero. */
export function HoldCountdown({ expiresAt, onExpire }: { expiresAt: string; onExpire?: () => void }) {
  const [left, setLeft] = useState(() => remainingMs(expiresAt))
  const fired = useRef(false)

  useEffect(() => {
    const tick = () => {
      const ms = remainingMs(expiresAt)
      setLeft(ms)
      if (ms === 0 && !fired.current) {
        fired.current = true
        onExpire?.()
      }
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [expiresAt, onExpire])

  const totalSeconds = Math.ceil(left / 1000)
  const mm = Math.floor(totalSeconds / 60)
  const ss = String(totalSeconds % 60).padStart(2, '0')
  const urgent = totalSeconds <= 60
  return (
    <p
      role="timer"
      aria-label={`Tickets held for ${mm} minutes ${ss} seconds`}
      className={urgent ? 'flex items-center gap-1.5 text-sm font-medium text-brand-coral' : 'flex items-center gap-1.5 text-sm text-muted-foreground'}
    >
      <Timer className="size-4" aria-hidden />
      Tickets held for {mm}:{ss}
    </p>
  )
}
