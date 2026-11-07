import type { DayPoint } from '@/features/organizer/dashboard/types'

export interface AdminOverview {
  days: number
  gmvPaise: number
  platformRevenuePaise: number
  refundsPaise: number
  bookings: number
  ticketsSold: number
  newUsers: number
  totalUsers: number
  liveEvents: number
  pendingApplications: number
  pendingEvents: number
  openReports: number
  failedRefunds: number
  openDisputes: number
  daily: DayPoint[]
}

export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface AdminUser {
  id: string
  email: string
  fullName: string | null
  roles: string[]
  status: UserStatus
  createdAt: string
  suspendedAt: string | null
  suspensionReason: string | null
}

export type ManagedOrganizerStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

export interface AdminOrganizerRow {
  id: string
  orgName: string
  slug: string
  contactEmail: string
  status: ManagedOrganizerStatus
  eventsCount: number
  payablePaise: number
  negative: boolean
  appliedAt: string
}

export interface Balances {
  pendingPaise: number
  payablePaise: number
  paidOutPaise: number
  balancePaise: number
  negative: boolean
}

export interface PayoutRow {
  id: string
  amountPaise: number
  reference: string
  paidAt: string
  recordedByEmail: string
  note: string | null
}

export interface AdminOrganizerDetail {
  id: string
  userId: string
  orgName: string
  slug: string
  status: ManagedOrganizerStatus
  contactEmail: string
  contactPhone: string | null
  website: string | null
  pan: string
  gstin: string | null
  state: string | null
  payoutAccountName: string | null
  payoutAccountMasked: string | null
  payoutIfsc: string | null
  commissionBpsOverride: number | null
  appliedAt: string
  reviewedAt: string | null
  balances: Balances
  payouts: PayoutRow[]
}

export interface OrganizerUpdate {
  orgName?: string
  pan?: string
  gstin?: string
  commissionBpsOverride?: number | null
}

export interface AdminPaymentRow {
  id: string
  bookingId: string
  bookingCode: string
  eventTitle: string
  attendeeEmail: string
  amountPaise: number
  status: string
  method: string | null
  razorpayPaymentId: string | null
  gatewayFeePaise: number | null
  createdAt: string
}

export interface AdminRefundRow {
  id: string
  bookingId: string
  bookingCode: string
  eventTitle: string
  kind: string
  status: string
  amountPaise: number
  attempts: number
  needsRetry: boolean
  failureReason: string | null
  createdAt: string
}

export interface AdminDisputeRow {
  id: string
  razorpayDisputeId: string
  bookingId: string
  bookingCode: string
  eventTitle: string
  amountPaise: number
  reason: string | null
  status: string
  createdAt: string
}

export interface OrganizerPayoutSummary {
  organizerId: string
  orgName: string
  status: string
  pendingPaise: number
  payablePaise: number
  paidOutPaise: number
  negative: boolean
  lastPayoutAt: string | null
}

export interface RecordPayoutInput {
  amountPaise: number
  reference: string
  paidOn: string
  note?: string
}

export interface FeeSettings {
  convenienceFeeBps: number
  convenienceFeeFlatPaise: number
  gstBps: number
  commissionBps: number
  refundFeeOnAttendeeRequest: boolean
}

export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'

export interface AdminReportRow {
  id: string
  eventId: string
  eventTitle: string
  eventSlug: string
  organizerName: string
  reason: string
  details: string | null
  reporterEmail: string
  status: ReportStatus
  resolutionNote: string | null
  createdAt: string
  openReportsForEvent: number
}

export interface AuditRow {
  id: string
  at: string
  actorEmail: string | null
  action: string
  entityType: string
  entityId: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
}
