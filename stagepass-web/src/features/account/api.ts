import { useQuery } from '@tanstack/react-query'
import type { PublicEventCard } from '@/features/discovery/types'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export interface SavedEvent {
  event: PublicEventCard
  status: string
  savedAt: string
}

export function useSavedEvents(page = 0) {
  return useQuery({
    queryKey: ['saved-events', page],
    queryFn: () => api<PageResponse<SavedEvent>>(`/me/saved?page=${page}&size=24`),
    placeholderData: (prev) => prev,
  })
}
