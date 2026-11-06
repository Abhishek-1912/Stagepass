import type { CartItem } from './types'

const ID = /^[0-9a-f-]{8,36}$/i

/** `items=typeA:2,typeB:1` → cart lines. Bad or duplicate entries are dropped. */
export function parseItems(param: string | null): CartItem[] {
  if (!param) return []
  const seen = new Set<string>()
  const items: CartItem[] = []
  for (const part of param.split(',')) {
    const [id, qty] = part.split(':')
    const quantity = Number(qty)
    if (!id || !ID.test(id) || seen.has(id) || !Number.isInteger(quantity) || quantity < 1 || quantity > 100) continue
    seen.add(id)
    items.push({ ticketTypeId: id, quantity })
  }
  return items
}

export function serializeItems(items: CartItem[]): string {
  return items.map((i) => `${i.ticketTypeId}:${i.quantity}`).join(',')
}

/** Order-independent identity of a cart. */
export function cartSignature(items: CartItem[]): string {
  return [...items]
    .sort((a, b) => a.ticketTypeId.localeCompare(b.ticketTypeId))
    .map((i) => `${i.ticketTypeId}:${i.quantity}`)
    .join(',')
}
