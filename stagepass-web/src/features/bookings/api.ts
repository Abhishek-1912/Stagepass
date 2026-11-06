import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { bookingQueryKey } from '@/features/checkout/api'
import type { Booking, BookingStatus } from '@/features/checkout/types'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export { useMyBooking } from '@/features/checkout/api'

export interface BookingSummary {
  id: string
  code: string
  status: BookingStatus
  eventTitle: string
  eventSlug: string
  coverImageUrl: string | null
  eventStartsAt: string
  city: string | null
  ticketCount: number
  totalPaise: number
}

export type BookingScope = 'upcoming' | 'past'

export function useMyBookings(scope: BookingScope, page = 0) {
  return useQuery({
    queryKey: ['my-bookings', scope, page],
    queryFn: () => api<PageResponse<BookingSummary>>(`/me/bookings?scope=${scope}&page=${page}&size=20`),
    placeholderData: keepPreviousData,
  })
}

export function useRequestRefund(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { ticketIds: string[]; reason?: string }) =>
      api<Booking>(`/me/bookings/${bookingId}/refunds`, { method: 'POST', json: body }),
    onSuccess: (booking) => {
      queryClient.setQueryData(bookingQueryKey(booking.id), booking)
      void queryClient.invalidateQueries({ queryKey: ['my-bookings'] })
    },
  })
}
