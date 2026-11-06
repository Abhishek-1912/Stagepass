import { afterEach, expect, test } from 'vitest'
import { parseItems, serializeItems } from './cart'
import { forgetIdempotencyKey, idempotencyKeyFor } from './idempotency'

afterEach(() => sessionStorage.clear())

const A = 'aaaaaaaa-0000-0000-0000-000000000001'
const B = 'bbbbbbbb-0000-0000-0000-000000000002'

test('idempotency key is stable for the same cart and changes with it', () => {
  const first = idempotencyKeyFor('gig', [{ ticketTypeId: A, quantity: 2 }, { ticketTypeId: B, quantity: 1 }])
  const again = idempotencyKeyFor('gig', [{ ticketTypeId: B, quantity: 1 }, { ticketTypeId: A, quantity: 2 }])
  const changed = idempotencyKeyFor('gig', [{ ticketTypeId: A, quantity: 3 }, { ticketTypeId: B, quantity: 1 }])
  const otherEvent = idempotencyKeyFor('other', [{ ticketTypeId: A, quantity: 2 }, { ticketTypeId: B, quantity: 1 }])

  expect(first).toHaveLength(36)
  expect(again).toBe(first)
  expect(changed).not.toBe(first)
  expect(otherEvent).not.toBe(first)
})

test('forgetting a key starts a fresh checkout next time', () => {
  const items = [{ ticketTypeId: A, quantity: 1 }]
  const first = idempotencyKeyFor('gig', items)
  forgetIdempotencyKey('gig', items)
  expect(idempotencyKeyFor('gig', items)).not.toBe(first)
})

test('cart parses, drops junk and round-trips', () => {
  expect(parseItems(`${A}:2,${B}:1,${A}:5,nope:1,${B.replace('b', 'c')}:0,x`)).toEqual([
    { ticketTypeId: A, quantity: 2 },
    { ticketTypeId: B, quantity: 1 },
  ])
  expect(parseItems(serializeItems([{ ticketTypeId: A, quantity: 3 }]))).toEqual([{ ticketTypeId: A, quantity: 3 }])
  expect(parseItems(null)).toEqual([])
})
