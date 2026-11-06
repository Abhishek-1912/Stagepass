import { expect, test } from 'vitest'
import { formatPaise, rupeesToPaise } from './money'

test('rupeesToPaise handles whole and decimal rupees', () => {
  expect(rupeesToPaise('499')).toBe(49900)
  expect(rupeesToPaise('499.5')).toBe(49950)
  expect(rupeesToPaise(' 1,499.00 ')).toBe(149900)
  expect(rupeesToPaise('0')).toBe(0)
})

test('rupeesToPaise rejects three decimals, negatives and junk', () => {
  expect(() => rupeesToPaise('1.234')).toThrow()
  expect(() => rupeesToPaise('-5')).toThrow()
  expect(() => rupeesToPaise('abc')).toThrow()
  expect(() => rupeesToPaise('')).toThrow()
})

test('formatPaise uses Indian grouping and shows Free for zero', () => {
  expect(formatPaise(49900)).toBe('₹499')
  expect(formatPaise(49950)).toBe('₹499.50')
  expect(formatPaise(14990000)).toBe('₹1,49,900')
  expect(formatPaise(0)).toBe('Free')
})
