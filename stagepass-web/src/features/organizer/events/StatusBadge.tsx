import type { EventStatus } from '@/lib/api/types'
import { cn } from '@/lib/utils'

const styles: Record<EventStatus, { label: string; className: string }> = {
  DRAFT: { label: 'Draft', className: 'bg-muted text-muted-foreground' },
  PENDING_REVIEW: { label: 'In review', className: 'bg-warning/15 text-warning' },
  PUBLISHED: { label: 'Live', className: 'bg-success/15 text-success' },
  REJECTED: { label: 'Changes needed', className: 'bg-destructive/10 text-destructive' },
  CANCELLED: { label: 'Cancelled', className: 'bg-destructive/10 text-destructive' },
  COMPLETED: { label: 'Completed', className: 'bg-secondary text-secondary-foreground' },
}

export function StatusBadge({ status, className }: { status: EventStatus; className?: string }) {
  const s = styles[status]
  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', s.className, className)}>
      {s.label}
    </span>
  )
}
