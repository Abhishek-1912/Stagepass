import type { Venue } from '@/lib/api/types'

/** Mirrors of the public discovery DTOs (`/api/v1/public/**`). */

export interface PublicEventCard {
  id: string
  slug: string
  title: string
  summary: string | null
  coverImageUrl: string | null
  startsAt: string
  endsAt: string
  online: boolean
  venueName: string | null
  city: string | null
  categorySlug: string
  categoryName: string
  minPricePaise: number | null
  soldOut: boolean
  featured: boolean
  distanceKm: number | null
}

export interface CategoryRail {
  slug: string
  name: string
  icon: string | null
  events: PublicEventCard[]
}

export interface HomeFeed {
  featured: PublicEventCard[]
  trending: PublicEventCard[]
  thisWeekend: PublicEventCard[]
  upcoming: PublicEventCard[]
  nearby: PublicEventCard[]
  categories: CategoryRail[]
}

export interface City {
  name: string
  upcomingEvents: number
}

export type TicketAvailability = 'ON_SALE' | 'SOLD_OUT' | 'NOT_STARTED' | 'ENDED'

export interface PublicTicketType {
  id: string
  name: string
  description: string | null
  pricePaise: number
  availability: TicketAvailability
  saleStartsAt: string | null
  saleEndsAt: string | null
  minPerOrder: number
  maxPerOrder: number
  fewLeft: boolean
}

export interface PublicOrganizer {
  name: string
  slug: string
  logoUrl: string | null
  bio: string | null
  website: string | null
}

export interface PublicEventDetail {
  id: string
  slug: string
  title: string
  summary: string | null
  description: string | null
  coverImageUrl: string | null
  gallery: string[]
  startsAt: string
  endsAt: string
  timezone: string
  status: 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'
  online: boolean
  venue: Venue | null
  categorySlug: string
  categoryName: string
  terms: string | null
  maxTicketsPerUser: number | null
  refundPolicy: 'NONE' | 'UNTIL_HOURS_BEFORE'
  refundCutoffHours: number | null
  soldOut: boolean
  minPricePaise: number | null
  ticketTypes: PublicTicketType[]
  organizer: PublicOrganizer
  rating: RatingSummary
}

export interface RatingSummary {
  /** One decimal; null when there are no reviews. */
  average: number | null
  count: number
}

export interface PublicOrganizerProfile {
  organizer: PublicOrganizer
  upcoming: PublicEventCard[]
  upcomingCount: number
  pastEvents: number
  rating: RatingSummary
}
