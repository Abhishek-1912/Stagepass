import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { EventStats, Overview, Range } from './types'

export function useOverview(days: Range) {
  return useQuery({
    queryKey: ['organizer', 'overview', days],
    queryFn: () => api<Overview>(`/organizer/overview?days=${days}`),
    placeholderData: keepPreviousData,
  })
}

export function useEventStats(eventId: string | undefined, days: Range) {
  return useQuery({
    queryKey: ['organizer', 'event-stats', eventId, days],
    queryFn: () => api<EventStats>(`/organizer/events/${eventId}/stats?days=${days}`),
    enabled: !!eventId,
    placeholderData: keepPreviousData,
  })
}
