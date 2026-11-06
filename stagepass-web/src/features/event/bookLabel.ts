import type { PublicEventDetail } from '@/features/discovery/types'

/** Whether at least one ticket type can be bought right now. */
export function canBook(event: PublicEventDetail): boolean {
  return event.status === 'PUBLISHED' && event.ticketTypes.some((t) => t.availability === 'ON_SALE')
}

/** Text of the main call to action on an event page. */
export function bookLabel(event: PublicEventDetail): string {
  if (event.status === 'CANCELLED') return 'Event cancelled'
  if (event.status === 'COMPLETED') return 'Event ended'
  if (canBook(event)) return 'Book tickets'
  if (event.ticketTypes.some((t) => t.availability === 'NOT_STARTED')) return 'Tickets on sale soon'
  if (event.soldOut) return 'Sold out'
  return 'Sales closed'
}
