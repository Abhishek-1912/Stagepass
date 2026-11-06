import type { Booking, RefundInfo } from '@/features/checkout/types'

export function refundInfo(overrides: Partial<RefundInfo> = {}): RefundInfo {
  return {
    selfServe: true,
    requestable: true,
    kind: 'ATTENDEE_REQUEST',
    includesFee: false,
    until: '2026-12-18T13:30:00Z',
    message: 'Ticket price refundable until 48 hours before the event (convenience fee is not refunded).',
    history: [],
    ...overrides,
  }
}

export function testBooking(overrides: Partial<Booking> = {}): Booking {
  return {
    id: 'b1',
    code: 'SP-ABC234',
    status: 'CONFIRMED',
    eventId: 'e1',
    eventTitle: 'Sunburn Goa',
    eventSlug: 'sunburn-goa-abc123',
    eventStartsAt: '2026-12-20T13:30:00Z',
    venueName: 'Vagator Beach',
    city: 'Goa',
    items: [{ ticketTypeId: 't1', name: 'General', quantity: 2, unitPricePaise: 49900, amountPaise: 99800 }],
    price: { subtotalPaise: 99800, feePaise: 4495, feeGstPaise: 809, totalPaise: 105104, commissionPaise: 0, ticketCount: 2 },
    tickets: [
      { id: 'k1', code: 'SP-AAAA-2222', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'VALID' },
      { id: 'k2', code: 'SP-BBBB-3333', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'VALID' },
    ],
    attendeeName: 'Asha Rao',
    attendeeEmail: 'asha@test.in',
    invoiceNumber: 'SP/2026-27/000042',
    refund: refundInfo(),
    ...overrides,
  }
}
