import { expect, test } from 'vitest'
import { formatEventDate, isoToIstInput, istInputToIso } from './datetime'

test('istInputToIso adds the IST offset', () => {
  expect(istInputToIso('2026-12-20T19:00')).toBe('2026-12-20T19:00:00+05:30')
})

test('isoToIstInput converts UTC to IST local time', () => {
  expect(isoToIstInput('2026-12-20T13:30:00Z')).toBe('2026-12-20T19:00')
  expect(isoToIstInput('2026-12-20T19:00:00+05:30')).toBe('2026-12-20T19:00')
})

test('isoToIstInput crosses midnight correctly', () => {
  expect(isoToIstInput('2026-12-31T20:00:00Z')).toBe('2027-01-01T01:30')
})

test('formatEventDate shows weekday, date and IST time', () => {
  const text = formatEventDate('2026-12-20T13:30:00Z')
  expect(text).toContain('Sun')
  expect(text).toContain('20 Dec 2026')
  expect(text).toMatch(/7:00\s?PM/i)
})
