import { Star } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Read-only stars; screen readers hear "4.5 out of 5 stars". */
export function Stars({ value, className }: { value: number; className?: string }) {
  const label = `${Number.isInteger(value) ? value : value.toFixed(1)} out of 5 stars`
  return (
    <span role="img" aria-label={label} className={cn('inline-flex items-center gap-0.5', className)}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          aria-hidden
          className={cn('size-4', i <= Math.round(value) ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground/40')}
        />
      ))}
    </span>
  )
}
