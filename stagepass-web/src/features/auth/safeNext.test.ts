import { expect, test } from 'vitest'
import { safeNext } from './safeNext'

test('keeps same-site paths', () => {
  expect(safeNext('/account/bookings?tab=past')).toBe('/account/bookings?tab=past')
})

test('rejects absolute and protocol-relative URLs', () => {
  expect(safeNext('https://evil.example/phish')).toBe('/')
  expect(safeNext('//evil.example')).toBe('/')
  expect(safeNext('/\\evil.example')).toBe('/')
})

test('falls back when missing or pointing at auth pages', () => {
  expect(safeNext(null)).toBe('/')
  expect(safeNext('/login?next=/x')).toBe('/')
})
