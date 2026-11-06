import type { PublicEventCard } from '@/features/discovery/types'

/** Test factory for public event cards. */
export function card(overrides: Partial<PublicEventCard> = {}): PublicEventCard {
  return {
    id: 'e1',
    slug: 'sunburn-goa-abc123',
    title: 'Sunburn Goa',
    summary: 'Three stages',
    coverImageUrl: 'https://res.cloudinary.com/demo/image/upload/stagepass/events/e1/cover.jpg',
    startsAt: '2026-12-20T13:30:00Z',
    endsAt: '2026-12-20T18:30:00Z',
    online: false,
    venueName: 'Vagator Beach',
    city: 'Goa',
    categorySlug: 'festivals',
    categoryName: 'Festivals',
    minPricePaise: 149900,
    soldOut: false,
    featured: false,
    distanceKm: null,
    ...overrides,
  }
}
