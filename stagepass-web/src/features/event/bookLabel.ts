import type { PublicEventDetail } from '@/features/discovery/types'

/** Text of the (still disabled) Book button. Booking itself arrives with Plan 4. */
export function bookLabel(event: PublicEventDetail): string {
  if (event.status === 'CANCELLED') return 'Event cancelled'
  if (event.status === 'COMPLETED') return 'Event ended'
  if (event.soldOut) return 'Sold out'
  return 'Tickets on sale soon'
}

