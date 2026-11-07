import { Heart } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import { useOptionalAuth } from '@/features/auth/AuthProvider'
import { cn } from '@/lib/utils'
import { useSavedIds, useToggleSaved } from './useSaved'

const base =
  'grid size-9 place-items-center rounded-full bg-background/90 shadow-sm backdrop-blur transition-colors hover:bg-background focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'

function HeartButton({ saved, onClick, className }: { saved: boolean; onClick: (e: React.MouseEvent) => void; className?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={saved}
      aria-label={saved ? 'Remove from saved' : 'Save event'}
      className={cn(base, className)}
    >
      <Heart className={cn('size-4.5', saved ? 'fill-accent-foreground text-accent-foreground' : 'text-foreground')} aria-hidden />
    </button>
  )
}

function SignedInSave({ eventId, className }: { eventId: string; className?: string }) {
  const ids = useSavedIds()
  const toggle = useToggleSaved()
  const saved = !!ids.data?.includes(eventId)
  return (
    <HeartButton
      saved={saved}
      className={className}
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        toggle.mutate({ eventId, save: !saved })
      }}
    />
  )
}

function SignedOutSave({ className }: { className?: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <HeartButton
      saved={false}
      className={className}
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`)
      }}
    />
  )
}

/** Heart toggle for one event; signed-out users are sent to sign in and brought back. */
export function SaveButton({ eventId, className }: { eventId: string; className?: string }) {
  const auth = useOptionalAuth()
  return auth?.session ? <SignedInSave eventId={eventId} className={className} /> : <SignedOutSave className={className} />
}
