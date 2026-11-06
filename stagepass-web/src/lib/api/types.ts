/** TypeScript mirrors of the Stagepass API's DTOs. */

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type OrganizerStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

export interface OrganizerApplication {
  id: string
  userId?: string
  orgName: string
  slug: string
  status: OrganizerStatus
  rejectionReason: string | null
  contactEmail: string
  contactPhone: string | null
  website: string | null
  bio: string | null
  logoUrl: string | null
  pan: string
  gstin: string | null
  state: string | null
  payoutAccountName: string | null
  payoutAccountMasked: string | null
  payoutIfsc: string | null
  appliedAt: string
  reviewedAt: string | null
}

export interface OrganizerApplicationInput {
  orgName: string
  bio?: string
  logoUrl?: string
  contactEmail: string
  contactPhone?: string
  website?: string
  pan: string
  gstin?: string
  state: string
  payoutAccountName: string
  payoutAccountNumber: string
  payoutIfsc: string
}

export interface Category {
  id: string
  name: string
  slug: string
  icon: string | null
  sortOrder: number
  active: boolean
}

export interface Venue {
  id: string
  name: string
  addressLine: string
  city: string
  state: string | null
  pincode: string | null
  lat: number | null
  lng: number | null
  googlePlaceId: string | null
}

export type VenueInput = Partial<Omit<Venue, 'id'>>

export type EventStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'
export type RefundPolicy = 'NONE' | 'UNTIL_HOURS_BEFORE'
export type TicketTypeStatus = 'ACTIVE' | 'HIDDEN' | 'SOLD_OUT'

export interface TicketType {
  id: string
  name: string
  description: string | null
  pricePaise: number
  quantity: number
  sold: number
  held: number
  available: number
  saleStartsAt: string | null
  saleEndsAt: string | null
  minPerOrder: number
  maxPerOrder: number
  sortOrder: number
  status: TicketTypeStatus
}

export interface TicketTypeInput {
  name?: string
  description?: string
  pricePaise?: number
  quantity?: number
  saleStartsAt?: string
  saleEndsAt?: string
  minPerOrder?: number
  maxPerOrder?: number
  sortOrder?: number
  status?: TicketTypeStatus
}

export interface EventDetail {
  id: string
  slug: string
  title: string
  summary: string | null
  description: string | null
  categoryId: string
  categoryName: string
  venue: Venue | null
  online: boolean
  onlineUrl: string | null
  startsAt: string
  endsAt: string
  timezone: string
  coverImageUrl: string | null
  gallery: string[]
  terms: string | null
  maxTicketsPerUser: number | null
  refundPolicy: RefundPolicy
  refundCutoffHours: number | null
  status: EventStatus
  rejectionReason: string | null
  featured: boolean
  rescheduleRefundUntil: string | null
  submittedAt: string | null
  publishedAt: string | null
  organizerId: string
  organizerName: string
  ticketTypes: TicketType[]
}

export interface EventInput {
  title?: string
  summary?: string
  description?: string
  categoryId?: string
  venueId?: string
  online?: boolean
  onlineUrl?: string
  startsAt?: string
  endsAt?: string
  terms?: string
  maxTicketsPerUser?: number
  refundPolicy?: RefundPolicy
  refundCutoffHours?: number
  coverImageUrl?: string
  gallery?: string[]
}

export interface EventSummary {
  id: string
  title: string
  slug: string
  status: EventStatus
  startsAt: string
  city: string | null
  coverImageUrl: string | null
  ticketTypeCount: number
  minPricePaise: number | null
  totalCapacity: number
  sold: number
}

export interface AdminEventSummary {
  id: string
  title: string
  organizerName: string
  status: EventStatus
  startsAt: string
  submittedAt: string | null
  city: string | null
  coverImageUrl: string | null
  featured: boolean
  online: boolean
}

export type UploadPurpose = 'EVENT_IMAGE' | 'ORGANIZER_LOGO'
