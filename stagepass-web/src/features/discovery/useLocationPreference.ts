import { useCallback, useState } from 'react'

const CITY_KEY = 'stagepass-city'

type Status = 'idle' | 'locating' | 'denied' | 'granted'

function readCity(): string | null {
  try {
    return localStorage.getItem(CITY_KEY)
  } catch {
    return null
  }
}

/**
 * The visitor's chosen city (remembered on this device) and, only after they click "Use my location", their
 * approximate coordinates. Coordinates are rounded to ~1 km and never stored.
 */
export function useLocationPreference() {
  const [city, setCityState] = useState<string | null>(readCity)
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null)
  const [status, setStatus] = useState<Status>('idle')

  const setCity = useCallback((next: string | null) => {
    setCityState(next)
    setCoords(null)
    try {
      if (next) localStorage.setItem(CITY_KEY, next)
      else localStorage.removeItem(CITY_KEY)
    } catch {
      // storage unavailable (private mode): keep it in memory
    }
  }, [])

  const requestCoords = useCallback(
    () =>
      new Promise<void>((resolve) => {
        if (!navigator.geolocation) {
          setStatus('denied')
          resolve()
          return
        }
        setStatus('locating')
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            const round = (n: number) => Math.round(n * 100) / 100
            setCoords({ lat: round(pos.coords.latitude), lng: round(pos.coords.longitude) })
            setStatus('granted')
            resolve()
          },
          () => {
            setStatus('denied')
            resolve()
          },
          { enableHighAccuracy: false, timeout: 10_000, maximumAge: 10 * 60_000 },
        )
      }),
    [],
  )

  return { city, setCity, coords, requestCoords, status }
}
