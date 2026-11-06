import { expect, test } from 'vitest'
import { activeFilterCount, parseFilters, toSearchParams } from './filters'

test('round-trips filters and drops invalid values', () => {
  const parsed = parseFilters(
    new URLSearchParams('q=sunburn&city=Goa&category=festivals&date=weekend&minPrice=500&maxPrice=abc&sort=bogus&from=2026-13'),
  )
  expect(parsed).toEqual({ q: 'sunburn', city: 'Goa', category: 'festivals', date: 'weekend', minPrice: 500 })
  expect(toSearchParams(parsed).toString()).toBe('q=sunburn&city=Goa&category=festivals&date=weekend&minPrice=500')
})

test('footer price=free link becomes free=true', () => {
  expect(parseFilters(new URLSearchParams('price=free'))).toEqual({ free: true })
  expect(toSearchParams({ free: true }).toString()).toBe('free=true')
})

test('nearby sort needs coordinates', () => {
  expect(parseFilters(new URLSearchParams('sort=nearby'))).toEqual({})
  expect(parseFilters(new URLSearchParams('sort=nearby&lat=12.97&lng=77.59'))).toEqual({ sort: 'nearby', lat: 12.97, lng: 77.59 })
})

test('default sort is not written to the URL', () => {
  expect(toSearchParams({ sort: 'popular' }).toString()).toBe('')
})

test('counts active filters for the badge', () => {
  expect(activeFilterCount({ q: 'x', category: 'comedy', free: true })).toBe(2)
})
