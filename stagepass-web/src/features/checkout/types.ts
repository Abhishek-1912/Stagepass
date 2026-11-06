export interface CartItem {
  ticketTypeId: string
  quantity: number
}

export interface PriceBreakdown {
  subtotalPaise: number
  feePaise: number
  feeGstPaise: number
  totalPaise: number
  commissionPaise: number
  ticketCount: number
}

export interface QuoteLine {
  ticketTypeId: string
  name: string
  quantity: number
  unitPricePaise: number
  amountPaise: number
}

export interface Quote {
  lines: QuoteLine[]
  price: PriceBreakdown
  holdMinutes: number
}

export interface CheckoutInfo {
  razorpayKeyId: string
  razorpayOrderId: string
  amountPaise: number
  currency: string
  companyName: string
}

export interface TicketLine {
  id: string
  code: string
  ticketTypeId: string
  ticketTypeName: string
  holderName: string
  status: 'VALID' | 'CHECKED_IN' | 'CANCELLED' | 'REFUNDED'
}

export interface RefundLine {
  id: string
  status: 'REQUESTED' | 'PENDING_APPROVAL' | 'DECLINED' | 'PROCESSING' | 'PROCESSED' | 'FAILED'
  kind: string
  amountPaise: number
  ticketCount: number
  note: string | null
  createdAt: string
}

export interface RefundInfo {
  selfServe: boolean
  requestable: boolean
  kind: 'RESCHEDULE' | 'ATTENDEE_REQUEST' | 'NONE'
  includesFee: boolean
  until: string | null
  message: string
  history: RefundLine[]
}

export type BookingStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'

export interface Booking {
  id: string
  code: string
  status: BookingStatus
  eventId: string
  eventTitle: string
  eventSlug: string
  eventStartsAt: string
  venueName?: string
  city?: string
  items: QuoteLine[]
  price: PriceBreakdown
  holdExpiresAt?: string
  checkout?: CheckoutInfo
  tickets: TicketLine[]
  attendeeName: string
  attendeeEmail: string
  invoiceNumber?: string
  refund?: RefundInfo
}

export interface CreateBookingBody {
  eventId: string
  items: CartItem[]
  attendeeName: string
  attendeeEmail: string
  attendeePhone?: string
  buyerState?: string
}

export interface VerifyPaymentBody {
  bookingId: string
  razorpayOrderId: string
  razorpayPaymentId: string
  razorpaySignature: string
}
