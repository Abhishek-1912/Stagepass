import type { Booking } from '@/features/checkout/types'

/** Rough refund for the chosen tickets (pro-rata like the server); the exact amount comes back after submit. */
export function estimateRefund(booking: Booking, ticketIds: string[], includesFee: boolean): number {
  const unit = new Map(booking.items.map((i) => [i.ticketTypeId, i.unitPricePaise]))
  const chosen = booking.tickets.filter((t) => ticketIds.includes(t.id))
  const ticketPart = chosen.reduce((sum, t) => sum + (unit.get(t.ticketTypeId) ?? 0), 0)
  if (!includesFee || booking.price.subtotalPaise === 0) return ticketPart
  const fees = booking.price.feePaise + booking.price.feeGstPaise
  return ticketPart + Math.round((fees * ticketPart) / booking.price.subtotalPaise)
}
