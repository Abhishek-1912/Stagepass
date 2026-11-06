import { importLibrary, setOptions } from '@googlemaps/js-api-loader'
import { Loader2, Search } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { mapsKey } from '@/lib/maps'

export interface PickedPlace {
  name: string
  addressLine: string
  city: string
  state?: string
  pincode?: string
  lat?: number
  lng?: number
  googlePlaceId?: string
}

interface Suggestion {
  id: string
  text: string
  prediction: google.maps.places.PlacePrediction
}

let configured = false
async function places() {
  if (!configured) {
    setOptions({ key: mapsKey(), v: 'weekly', region: 'IN' })
    configured = true
  }
  return importLibrary('places')
}

function component(place: google.maps.places.Place, type: string) {
  return place.addressComponents?.find((c) => c.types.includes(type))?.longText ?? undefined
}

/** Venue search using Places API (New) autocomplete, limited to India. Only rendered when a Maps key exists. */
export function PlacesAutocomplete({ onPlace }: { onPlace: (place: PickedPlace) => void }) {
  const listId = useId()
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef<google.maps.places.AutocompleteSessionToken | null>(null)
  const visible = query.trim().length >= 3 ? suggestions : []

  useEffect(() => {
    const input = query.trim()
    if (input.length < 3) return
    const run = { cancelled: false }
    const t = setTimeout(async () => {
      try {
        const lib = await places()
        token.current ??= new lib.AutocompleteSessionToken()
        const { suggestions: found } = await lib.AutocompleteSuggestion.fetchAutocompleteSuggestions({
          input,
          sessionToken: token.current,
          includedRegionCodes: ['in'],
        })
        if (run.cancelled) return
        setSuggestions(
          found
            .map((s) => s.placePrediction)
            .filter((p): p is google.maps.places.PlacePrediction => !!p)
            .map((p) => ({ id: p.placeId, text: p.text.text, prediction: p })),
        )
        setError(null)
      } catch {
        if (!run.cancelled) setError('Place search is unavailable. Fill the address in manually.')
      }
    }, 250)
    return () => {
      run.cancelled = true
      clearTimeout(t)
    }
  }, [query])

  async function choose(s: Suggestion) {
    setBusy(true)
    try {
      const place = s.prediction.toPlace()
      await place.fetchFields({ fields: ['displayName', 'formattedAddress', 'addressComponents', 'location', 'id'] })
      const street = [component(place, 'street_number'), component(place, 'route'), component(place, 'sublocality_level_1')]
        .filter(Boolean)
        .join(', ')
      onPlace({
        name: place.displayName ?? s.text.split(',')[0],
        addressLine: street || place.formattedAddress || s.text,
        city: component(place, 'locality') ?? component(place, 'administrative_area_level_2') ?? '',
        state: component(place, 'administrative_area_level_1'),
        pincode: component(place, 'postal_code'),
        lat: place.location?.lat(),
        lng: place.location?.lng(),
        googlePlaceId: place.id,
      })
      setQuery('')
      setSuggestions([])
      token.current = null // a session ends with the details request
    } catch {
      setError('Couldn’t load that place. Fill the address in manually.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="relative space-y-1.5">
      <label htmlFor={`${listId}-input`} className="text-sm font-medium">
        Search for the venue
      </label>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <input
          id={`${listId}-input`}
          role="combobox"
          aria-expanded={visible.length > 0}
          aria-controls={listId}
          autoComplete="off"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="e.g. NSCI Dome, Mumbai"
          className="h-11 w-full rounded-xl border border-input bg-background pl-9 pr-9 text-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
        />
        {busy && <Loader2 className="absolute right-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />}
      </div>
      {visible.length > 0 && (
        <ul id={listId} role="listbox" className="absolute z-50 mt-1 w-full overflow-hidden rounded-xl border border-border bg-popover shadow-lg">
          {visible.map((s) => (
            <li key={s.id} role="option" aria-selected={false}>
              <button type="button" onClick={() => choose(s)} className="block w-full px-3 py-2.5 text-left text-sm hover:bg-accent">
                {s.text}
              </button>
            </li>
          ))}
        </ul>
      )}
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : (
        <p className="text-xs text-muted-foreground">Pick a result to fill the address and map pin, or type it below.</p>
      )}
    </div>
  )
}
