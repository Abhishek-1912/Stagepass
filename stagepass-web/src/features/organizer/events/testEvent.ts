import type { EventDetail, TicketType } from '@/lib/api/types'

export function ticketType(overrides: Partial<TicketType> = {}): TicketType {
  return {
    id: 't1',
    name: 'General',
    description: null,
    pricePaise: 49900,
    quantity: 300,
    sold: 0,
    held: 0,
    available: 300,
    saleStartsAt: null,
    saleEndsAt: null,
    minPerOrder: 1,
    maxPerOrder: 10,
    sortOrder: 0,
    status: 'ACTIVE',
    ...overrides,
  }
}

export function eventDetail(overrides: Partial<EventDetail> = {}): EventDetail {
  return {
    id: 'e1',
    slug: 'fixture-night-abc123',
    title: 'Fixture Night',
    summary: 'A night to remember',
    description: null,
    categoryId: 'c1',
    categoryName: 'Music',
    venue: { id: 'v1', name: 'Test Arena', addressLine: '1 MG Road', city: 'Bengaluru', state: 'Karnataka', pincode: '560001', lat: null, lng: null, googlePlaceId: null },
    online: false,
    onlineUrl: null,
    startsAt: '2099-12-20T13:30:00Z',
    endsAt: '2099-12-20T17:30:00Z',
    timezone: 'Asia/Kolkata',
    coverImageUrl: 'https://res.cloudinary.com/demo/image/upload/stagepass/events/e1/cover.jpg',
    gallery: [],
    terms: null,
    maxTicketsPerUser: null,
    refundPolicy: 'NONE',
    refundCutoffHours: null,
    status: 'DRAFT',
    rejectionReason: null,
    featured: false,
    rescheduleRefundUntil: null,
    submittedAt: null,
    publishedAt: null,
    organizerId: 'o1',
    organizerName: 'Acme Live',
    ticketTypes: [ticketType()],
    ...overrides,
  }
}
