import type { Venue } from '@/lib/api/types'

/** Browser Maps key (public by design; restrict it to the site's referrers in Google Cloud). Optional. */
export function mapsKey(): string | undefined {
  const key = import.meta.env.VITE_GOOGLE_MAPS_KEY as string | undefined
  return key && key.trim() ? key.trim() : undefined
}

function query(venue: Venue): string {
  if (venue.lat != null && venue.lng != null) return `${venue.lat},${venue.lng}`
  return [venue.name, venue.addressLine, venue.city, venue.pincode].filter(Boolean).join(', ')
}

export function mapsEmbedUrl(venue: Venue, key: string): string {
  return `https://www.google.com/maps/embed/v1/place?key=${encodeURIComponent(key)}&q=${encodeURIComponent(query(venue))}`
}

export function mapsSearchUrl(venue: Venue): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query(venue))}`
}
