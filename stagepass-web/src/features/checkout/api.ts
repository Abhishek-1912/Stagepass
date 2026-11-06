import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { cartSignature } from './cart'
import type { Booking, CartItem, CreateBookingBody, Quote, VerifyPaymentBody } from './types'

export const bookingQueryKey = (id: string | undefined) => ['booking', id] as const

/** Server price for a selection (nothing is held). */
export function useQuote(eventId: string | undefined, items: CartItem[]) {
  return useQuery({
    queryKey: ['quote', eventId, cartSignature(items)],
    queryFn: () => api<Quote>('/public/quote', { method: 'POST', json: { eventId, items } }),
    enabled: !!eventId && items.length > 0,
    staleTime: 30_000,
    retry: false,
    placeholderData: keepPreviousData,
  })
}

export function useCreateBooking() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ key, body }: { key: string; body: CreateBookingBody }) =>
      api<Booking>('/bookings', { method: 'POST', json: body, headers: { 'Idempotency-Key': key } }),
    onSuccess: (booking) => queryClient.setQueryData(bookingQueryKey(booking.id), booking),
  })
}

export function useVerifyPayment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: VerifyPaymentBody) => api<Booking>('/payments/verify', { method: 'POST', json: body }),
    onSuccess: (booking) => {
      queryClient.setQueryData(bookingQueryKey(booking.id), booking)
      void queryClient.invalidateQueries({ queryKey: ['my-bookings'] })
    },
  })
}

/** One of the signed-in user's bookings. */
export function useMyBooking(id: string | undefined) {
  return useQuery({
    queryKey: bookingQueryKey(id),
    queryFn: () => api<Booking>(`/me/bookings/${id}`),
    enabled: !!id,
  })
}
