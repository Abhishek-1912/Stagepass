import { act, renderHook } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { useLocationPreference } from './useLocationPreference'

afterEach(() => vi.unstubAllGlobals())

function stubGeolocation(impl: (ok: PositionCallback, fail: PositionErrorCallback) => void) {
  vi.stubGlobal('navigator', { ...navigator, geolocation: { getCurrentPosition: impl } })
}

test('persists the chosen city', () => {
  const { result } = renderHook(() => useLocationPreference())
  act(() => result.current.setCity('Pune'))
  expect(result.current.city).toBe('Pune')
  expect(localStorage.getItem('stagepass-city')).toBe('Pune')

  const again = renderHook(() => useLocationPreference())
  expect(again.result.current.city).toBe('Pune')
})

test('denied geolocation keeps the city and reports denied', async () => {
  localStorage.setItem('stagepass-city', 'Mumbai')
  stubGeolocation((_ok, fail) => fail({ code: 1, message: 'denied' } as GeolocationPositionError))
  const { result } = renderHook(() => useLocationPreference())

  await act(() => result.current.requestCoords())

  expect(result.current.status).toBe('denied')
  expect(result.current.coords).toBeNull()
  expect(result.current.city).toBe('Mumbai')
})

test('granted geolocation stores rounded coordinates', async () => {
  stubGeolocation((ok) => ok({ coords: { latitude: 12.971599, longitude: 77.594566 } } as GeolocationPosition))
  const { result } = renderHook(() => useLocationPreference())

  await act(() => result.current.requestCoords())

  expect(result.current.status).toBe('granted')
  expect(result.current.coords).toEqual({ lat: 12.97, lng: 77.59 })
})

test('does not ask for location until requested', () => {
  const spy = vi.fn()
  stubGeolocation(spy)
  renderHook(() => useLocationPreference())
  expect(spy).not.toHaveBeenCalled()
})
