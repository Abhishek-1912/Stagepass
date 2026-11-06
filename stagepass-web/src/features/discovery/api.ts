import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'
import { type ExploreFilters, toSearchParams } from './filters'
import type { City, HomeFeed, PublicEventCard, PublicEventDetail, PublicOrganizerProfile } from './types'

export function usePublicHome(params: { city?: string | null; lat?: number; lng?: number }) {
  const search = new URLSearchParams()
  if (params.city) search.set('city', params.city)
  if (params.lat != null && params.lng != null) {
    search.set('lat', String(params.lat))
    search.set('lng', String(params.lng))
  }
  const qs = search.toString()
  return useQuery({
    queryKey: ['public', 'home', qs],
    queryFn: () => api<HomeFeed>(`/public/home${qs ? `?${qs}` : ''}`),
    staleTime: 60_000,
  })
}

export function useCities() {
  return useQuery({ queryKey: ['public', 'cities'], queryFn: () => api<City[]>('/public/cities'), staleTime: 5 * 60_000 })
}

export const PAGE_SIZE = 24

export function usePublicEvents(filters: ExploreFilters) {
  const qs = toSearchParams(filters).toString()
  return useInfiniteQuery({
    queryKey: ['public', 'events', qs],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api<PageResponse<PublicEventCard>>(`/public/events?${qs}${qs ? '&' : ''}page=${pageParam}&size=${PAGE_SIZE}`),
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    staleTime: 60_000,
  })
}

export function usePublicEvent(slug: string | undefined) {
  return useQuery({
    queryKey: ['public', 'event', slug],
    queryFn: () => api<PublicEventDetail>(`/public/events/${slug}`),
    enabled: !!slug,
    staleTime: 30_000,
  })
}

export function usePublicOrganizer(slug: string | undefined) {
  return useQuery({
    queryKey: ['public', 'organizer', slug],
    queryFn: () => api<PublicOrganizerProfile>(`/public/organizers/${slug}`),
    enabled: !!slug,
  })
}
