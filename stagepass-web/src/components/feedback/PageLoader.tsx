import { Loader2 } from 'lucide-react'

export function PageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className="grid min-h-[50vh] place-items-center text-muted-foreground">
      <Loader2 className="size-6 animate-spin" />
      <span className="sr-only">{label}</span>
    </div>
  )
}
