import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export interface OrganizerBookingRow {
  id: string
  code: string
  status: string
  attendeeName: string
  attendeeEmail: string
  attendeePhone: string | null
  ticketCount: number
  totalPaise: number
  subtotalPaise: number
  createdAt: string
  confirmedAt: string | null
}

export interface AttendeeRow {
  ticketId: string
  ticketCode: string
  ticketType: string
  holderName: string
  bookingCode: string
  attendeeEmail: string
  attendeePhone: string | null
  status: string
  bookedAt: string | null
  checkedInAt: string | null
}

export interface OrganizerRefund {
  id: string
  bookingId: string
  bookingCode: string
  attendeeName: string
  attendeeEmail: string
  kind: string
  status: 'REQUESTED' | 'PENDING_APPROVAL' | 'DECLINED' | 'PROCESSING' | 'PROCESSED' | 'FAILED'
  ticketCount: number
  amountPaise: number
  reason: string | null
  note: string | null
  createdAt: string
}

export interface ListQuery {
  q: string
  status: string
  page: number
}

function qs({ q, status, page }: ListQuery) {
  const p = new URLSearchParams({ page: String(page), size: '20' })
  if (q.trim()) p.set('q', q.trim())
  if (status) p.set('status', status)
  return p.toString()
}

export function useEventBookings(eventId: string, query: ListQuery) {
  return useQuery({
    queryKey: ['organizer', 'event-bookings', eventId, query],
    queryFn: () => api<PageResponse<OrganizerBookingRow>>(`/organizer/events/${eventId}/bookings?${qs(query)}`),
    placeholderData: keepPreviousData,
  })
}

export function useEventAttendees(eventId: string, query: ListQuery) {
  return useQuery({
    queryKey: ['organizer', 'event-attendees', eventId, query],
    queryFn: () => api<PageResponse<AttendeeRow>>(`/organizer/events/${eventId}/attendees?${qs(query)}`),
    placeholderData: keepPreviousData,
  })
}

export function useRefundRequests(eventId: string) {
  return useQuery({
    queryKey: ['organizer', 'refunds', eventId],
    queryFn: () => api<OrganizerRefund[]>(`/organizer/events/${eventId}/refund-requests`),
  })
}

function useRefundDecision<T>(eventId: string, call: (arg: T) => Promise<OrganizerRefund>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: call,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['organizer', 'refunds', eventId] })
      void queryClient.invalidateQueries({ queryKey: ['organizer', 'event-stats', eventId] })
      void queryClient.invalidateQueries({ queryKey: ['organizer', 'event-bookings', eventId] })
    },
  })
}

export function useApproveRefund(eventId: string) {
  return useRefundDecision(eventId, (id: string) =>
    api<OrganizerRefund>(`/organizer/refund-requests/${id}/approve`, { method: 'POST', json: {} }),
  )
}

export function useDeclineRefund(eventId: string) {
  return useRefundDecision(eventId, ({ id, note }: { id: string; note: string }) =>
    api<OrganizerRefund>(`/organizer/refund-requests/${id}/decline`, { method: 'POST', json: { note } }),
  )
}
