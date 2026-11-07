import type { FeeSettings } from './types'

/** Same rounding as the server's FeeCalculator.halfUp. */
function halfUp(numerator: number, denominator: number): number {
  return Math.floor((numerator * 2 + denominator) / (denominator * 2))
}

/** What an attendee pays for `count` tickets at `unitPaise` under these settings (mirrors FeeCalculator.price). */
export function feePreview(s: FeeSettings, unitPaise: number, count: number) {
  const subtotal = unitPaise * count
  if (subtotal === 0) return { subtotal: 0, fee: 0, gst: 0, total: 0 }
  const fee = halfUp(subtotal * s.convenienceFeeBps, 10_000) + s.convenienceFeeFlatPaise * count
  const gst = halfUp(fee * s.gstBps, 10_000)
  return { subtotal, fee, gst, total: subtotal + fee + gst }
}
