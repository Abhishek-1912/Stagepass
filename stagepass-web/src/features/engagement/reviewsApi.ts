import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export interface PublicReview {
  id: string
  rating: number
  comment: string | null
  authorName: string
  createdAt: string
  updatedAt: string
}

export interface MyReview {
  eligible: boolean
  review: PublicReview | null
}

export function useEventReviews(slug: string) {
  return useInfiniteQuery({
    queryKey: ['reviews', slug],
    queryFn: ({ pageParam }) => api<PageResponse<PublicReview>>(`/public/events/${slug}/reviews?page=${pageParam}&size=5`),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useMyReview(eventId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['my-review', eventId],
    queryFn: () => api<MyReview>(`/events/${eventId}/review/mine`),
    enabled,
  })
}

function useReviewMutation<T>(fn: (v: T) => Promise<unknown>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['reviews'] })
      qc.invalidateQueries({ queryKey: ['my-review'] })
      qc.invalidateQueries({ queryKey: ['public', 'event'] })
    },
  })
}

export function useSaveReview() {
  return useReviewMutation(({ eventId, rating, comment }: { eventId: string; rating: number; comment: string }) =>
    api<PublicReview>(`/events/${eventId}/review`, { method: 'PUT', json: comment ? { rating, comment } : { rating } }),
  )
}

export function useDeleteReview() {
  return useReviewMutation((eventId: string) => api<void>(`/events/${eventId}/review`, { method: 'DELETE' }))
}

export function useAdminRemoveReview() {
  return useReviewMutation((reviewId: string) => api<void>(`/admin/reviews/${reviewId}`, { method: 'DELETE' }))
}
