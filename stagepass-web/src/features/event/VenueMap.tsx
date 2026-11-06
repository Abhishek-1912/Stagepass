import { ExternalLink, MapPin } from 'lucide-react'
import type { Venue } from '@/lib/api/types'
import { mapsEmbedUrl, mapsKey, mapsSearchUrl } from '@/lib/maps'

export function VenueMap({ venue }: { venue: Venue }) {
  const key = mapsKey()
  const address = [venue.addressLine, venue.city, venue.state, venue.pincode].filter(Boolean).join(', ')
  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-card">
      {key ? (
        <iframe
          title={`Map of ${venue.name}`}
          src={mapsEmbedUrl(venue, key)}
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
          className="aspect-[16/9] w-full border-0"
          allowFullScreen
        />
      ) : (
        <div className="bg-gradient-brand/10 grid aspect-[16/9] place-items-center bg-muted text-muted-foreground">
          <MapPin className="size-8" />
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div>
          <p className="font-medium">{venue.name}</p>
          <p className="text-sm text-muted-foreground">{address}</p>
        </div>
        <a
          href={mapsSearchUrl(venue)}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
        >
          Open in Google Maps <ExternalLink className="size-3.5" />
        </a>
      </div>
    </div>
  )
}
