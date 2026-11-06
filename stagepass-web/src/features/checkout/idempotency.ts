import { cartSignature } from './cart'
import type { CartItem } from './types'

function storageKey(slug: string, items: CartItem[]) {
  return `stagepass:checkout:${slug}:${cartSignature(items)}`
}

/**
 * Idempotency-Key for creating a booking. Stable for the same event + cart within this tab, so a double-click
 * or refresh returns the same booking instead of holding tickets twice; a different cart gets a new key.
 */
export function idempotencyKeyFor(slug: string, items: CartItem[]): string {
  const key = storageKey(slug, items)
  let value = sessionStorage.getItem(key)
  if (!value) {
    value = crypto.randomUUID()
    sessionStorage.setItem(key, value)
  }
  return value
}

/** Call once a booking is finished (paid, free, or expired) so buying the same cart again starts fresh. */
export function forgetIdempotencyKey(slug: string, items: CartItem[]): void {
  sessionStorage.removeItem(storageKey(slug, items))
}
