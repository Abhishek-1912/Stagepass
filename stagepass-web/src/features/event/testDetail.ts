import type { PublicEventDetail, PublicTicketType } from '@/features/discovery/types'

export function publicType(overrides: Partial<PublicTicketType> = {}): PublicTicketType {
  return {
    id: 't1',
    name: 'General',
    description: null,
    pricePaise: 49900,
    availability: 'ON_SALE',
    saleStartsAt: null,
    saleEndsAt: null,
    minPerOrder: 1,
    maxPerOrder: 10,
    fewLeft: false,
    ...overrides,
  }
}

export function publicEvent(overrides: Partial<PublicEventDetail> = {}): PublicEventDetail {
  return {
    rating: { average: null, count: 0 },
    id: 'e1',
    slug: 'sunburn-goa-abc123',
    title: 'Sunburn Goa',
    summary: 'Three stages, one night',
    description: 'Line-up to be announced',
    coverImageUrl: 'https://res.cloudinary.com/demo/image/upload/stagepass/events/e1/cover.jpg',
    gallery: [],
    startsAt: '2026-12-20T13:30:00Z',
    endsAt: '2026-12-20T18:30:00Z',
    timezone: 'Asia/Kolkata',
    status: 'PUBLISHED',
    online: false,
    venue: { id: 'v1', name: 'Vagator Beach', addressLine: 'Vagator', city: 'Goa', state: 'Goa', pincode: '403509', lat: 15.6, lng: 73.73, googlePlaceId: null },
    categorySlug: 'festivals',
    categoryName: 'Festivals',
    terms: 'No re-entry',
    maxTicketsPerUser: 6,
    refundPolicy: 'UNTIL_HOURS_BEFORE',
    refundCutoffHours: 48,
    soldOut: false,
    minPricePaise: 49900,
    ticketTypes: [publicType()],
    organizer: { name: 'Acme Live', slug: 'acme-live-x1', logoUrl: null, bio: 'Gigs since 2015', website: null },
    ...overrides,
  }
}
