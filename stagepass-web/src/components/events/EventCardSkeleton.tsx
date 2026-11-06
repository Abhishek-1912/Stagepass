import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export function EventCardSkeleton({ className }: { className?: string }) {
  return (
    <div aria-hidden className={cn('overflow-hidden rounded-2xl border border-border bg-card', className)}>
      <Skeleton className="aspect-[3/2] w-full rounded-none" />
      <div className="space-y-2 p-4">
        <Skeleton className="h-3 w-1/3" />
        <Skeleton className="h-4 w-4/5" />
        <Skeleton className="h-4 w-2/3" />
      </div>
    </div>
  )
}
