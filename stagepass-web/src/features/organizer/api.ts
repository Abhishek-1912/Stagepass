import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { meQueryKey } from '@/features/auth/useMe'
import { isApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/client'
import type {
  Category,
  EventDetail,
  EventInput,
  EventStatus,
  EventSummary,
  OrganizerApplication,
  OrganizerApplicationInput,
  PageResponse,
  TicketType,
  TicketTypeInput,
  UploadPurpose,
  Venue,
  VenueInput,
} from '@/lib/api/types'
import type { UploadSignature } from '@/lib/cloudinary'

export const organizerKeys = {
  application: ['organizer-application'] as const,
  events: (status?: EventStatus) => ['organizer', 'events', status ?? 'all'] as const,
  event: (id: string) => ['organizer', 'event', id] as const,
  venues: ['organizer', 'venues'] as const,
  categories: ['categories'] as const,
}

// ---- Application -------------------------------------------------------------------------------

export function useMyApplication() {
  return useQuery({
    queryKey: organizerKeys.application,
    queryFn: () => api<OrganizerApplication>('/organizer-applications/me'),
    retry: (count, error) => !(isApiError(error) && error.status === 404) && count < 1,
  })
}

export function useApply() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: OrganizerApplicationInput) =>
      api<OrganizerApplication>('/organizer-applications', { method: 'POST', json: input }),
    onSuccess: (data) => {
      qc.setQueryData(organizerKeys.application, data)
      qc.invalidateQueries({ queryKey: meQueryKey })
    },
  })
}

// ---- Uploads -----------------------------------------------------------------------------------

export function requestUploadSignature(purpose: UploadPurpose, eventId?: string) {
  return purpose === 'EVENT_IMAGE'
    ? api<UploadSignature>('/organizer/uploads/signature', { method: 'POST', json: { purpose, eventId } })
    : api<UploadSignature>('/uploads/signature', { method: 'POST', json: { purpose } })
}

// ---- Reference data ----------------------------------------------------------------------------

export function useCategories() {
  return useQuery({
    queryKey: organizerKeys.categories,
    queryFn: () => api<Category[]>('/public/categories'),
    staleTime: 5 * 60_000,
  })
}

// ---- Venues ------------------------------------------------------------------------------------

export function useVenues() {
  return useQuery({ queryKey: organizerKeys.venues, queryFn: () => api<Venue[]>('/organizer/venues') })
}

export function useSaveVenue() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: VenueInput }) =>
      id
        ? api<Venue>(`/organizer/venues/${id}`, { method: 'PATCH', json: input })
        : api<Venue>('/organizer/venues', { method: 'POST', json: input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: organizerKeys.venues }),
  })
}

// ---- Events ------------------------------------------------------------------------------------

export function useOrganizerEvents(status?: EventStatus) {
  return useQuery({
    queryKey: organizerKeys.events(status),
    queryFn: () => api<PageResponse<EventSummary>>(`/organizer/events?size=100${status ? `&status=${status}` : ''}`),
  })
}

export function useOrganizerEvent(id: string | undefined) {
  return useQuery({
    queryKey: organizerKeys.event(id ?? 'new'),
    queryFn: () => api<EventDetail>(`/organizer/events/${id}`),
    enabled: !!id,
  })
}

function useEventMutation<TVars>(fn: (vars: TVars) => Promise<EventDetail>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (event) => {
      qc.setQueryData(organizerKeys.event(event.id), event)
      qc.invalidateQueries({ queryKey: ['organizer', 'events'] })
    },
  })
}

export function useCreateEvent() {
  return useEventMutation((input: EventInput) => api<EventDetail>('/organizer/events', { method: 'POST', json: input }))
}

export function useUpdateEvent(id: string) {
  return useEventMutation((input: EventInput) =>
    api<EventDetail>(`/organizer/events/${id}`, { method: 'PATCH', json: input }),
  )
}

export function useSubmitEvent(id: string) {
  return useEventMutation(() => api<EventDetail>(`/organizer/events/${id}/submit`, { method: 'POST' }))
}

export function useCancelEvent(id: string) {
  return useEventMutation((reason: string) =>
    api<EventDetail>(`/organizer/events/${id}/cancel`, { method: 'POST', json: { reason } }),
  )
}

export function useDeleteEvent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/organizer/events/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['organizer', 'events'] }),
  })
}

// ---- Ticket types ------------------------------------------------------------------------------

export function useSaveTicketType(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: TicketTypeInput }) =>
      id
        ? api<TicketType>(`/organizer/events/${eventId}/ticket-types/${id}`, { method: 'PATCH', json: input })
        : api<TicketType>(`/organizer/events/${eventId}/ticket-types`, { method: 'POST', json: input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: organizerKeys.event(eventId) }),
  })
}

export function useDeleteTicketType(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/organizer/events/${eventId}/ticket-types/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: organizerKeys.event(eventId) }),
  })
}
