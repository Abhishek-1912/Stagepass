import type { BookingStatus } from '@/features/checkout/types'
import { cn } from '@/lib/utils'

const LABELS: Record<BookingStatus, { text: string; tone: string }> = {
  CONFIRMED: { text: 'Confirmed', tone: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' },
  PENDING_PAYMENT: { text: 'Payment pending', tone: 'bg-amber-500/15 text-amber-700 dark:text-amber-300' },
  PARTIALLY_REFUNDED: { text: 'Partly refunded', tone: 'bg-secondary text-secondary-foreground' },
  REFUNDED: { text: 'Refunded', tone: 'bg-muted text-muted-foreground' },
  EXPIRED: { text: 'Expired', tone: 'bg-muted text-muted-foreground' },
  CANCELLED: { text: 'Cancelled', tone: 'bg-brand-coral/15 text-brand-coral' },
}

export function BookingStatusBadge({ status, className }: { status: BookingStatus; className?: string }) {
  const { text, tone } = LABELS[status]
  return <span className={cn('inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold', tone, className)}>{text}</span>
}
