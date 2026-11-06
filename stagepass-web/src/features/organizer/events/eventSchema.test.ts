import { expect, test } from 'vitest'
import { basicsSchema, ticketTypeFormSchema, whenWhereSchema } from './eventSchema'

test('when/where requires end after start', () => {
  const result = whenWhereSchema.safeParse({ startsAt: '2026-12-20T19:00', endsAt: '2026-12-20T18:00', online: false, venueId: 'v1' })
  expect(result.success).toBe(false)
  expect(result.error!.issues[0].path).toEqual(['endsAt'])
})

test('online events need a link, in-person events need a venue', () => {
  const base = { startsAt: '2026-12-20T19:00', endsAt: '2026-12-20T22:00' }
  expect(whenWhereSchema.safeParse({ ...base, online: true, onlineUrl: '' }).error!.issues[0].path).toEqual(['onlineUrl'])
  expect(whenWhereSchema.safeParse({ ...base, online: false, venueId: '' }).error!.issues[0].path).toEqual(['venueId'])
  expect(whenWhereSchema.safeParse({ ...base, online: true, onlineUrl: 'https://meet.example/x' }).success).toBe(true)
})

test('basics requires title and category', () => {
  const result = basicsSchema.safeParse({ title: 'Hi', categoryId: '' })
  expect(result.error!.issues.map((i) => i.path[0]).sort()).toEqual(['categoryId', 'title'])
})

test('ticket type form converts rupees to paise', () => {
  const parsed = ticketTypeFormSchema.parse({ name: 'VIP', price: '1,499', quantity: '50', minPerOrder: '1', maxPerOrder: '4' })
  expect(parsed.pricePaise).toBe(149900)
  expect(parsed.quantity).toBe(50)
})

test('ticket type form rejects max below min and bad prices', () => {
  expect(ticketTypeFormSchema.safeParse({ name: 'X', price: '1.234', quantity: '5', minPerOrder: '1', maxPerOrder: '4' }).success).toBe(false)
  const r = ticketTypeFormSchema.safeParse({ name: 'X', price: '10', quantity: '5', minPerOrder: '4', maxPerOrder: '2' })
  expect(r.error!.issues[0].path).toEqual(['maxPerOrder'])
})
