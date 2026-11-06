import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type {
  AdminEventSummary,
  Category,
  EventDetail,
  EventStatus,
  OrganizerApplication,
  OrganizerStatus,
  PageResponse,
} from '@/lib/api/types'

const keys = {
  applications: (status: OrganizerStatus) => ['admin', 'applications', status] as const,
  events: (status: EventStatus) => ['admin', 'events', status] as const,
  event: (id: string) => ['admin', 'event', id] as const,
  categories: ['admin', 'categories'] as const,
}

// ---- Organizer applications ---------------------------------------------------------------------

export function useAdminApplications(status: OrganizerStatus) {
  return useQuery({
    queryKey: keys.applications(status),
    queryFn: () => api<PageResponse<OrganizerApplication>>(`/admin/organizer-applications?status=${status}&size=100`),
  })
}

function useApplicationDecision<T>(fn: (vars: T) => Promise<OrganizerApplication>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'applications'] }) })
}

export function useApproveApplication() {
  return useApplicationDecision((id: string) =>
    api<OrganizerApplication>(`/admin/organizer-applications/${id}/approve`, { method: 'POST' }),
  )
}

export function useRejectApplication() {
  return useApplicationDecision(({ id, reason }: { id: string; reason: string }) =>
    api<OrganizerApplication>(`/admin/organizer-applications/${id}/reject`, { method: 'POST', json: { reason } }),
  )
}

// ---- Event review -------------------------------------------------------------------------------

export function useAdminEvents(status: EventStatus) {
  return useQuery({
    queryKey: keys.events(status),
    queryFn: () => api<PageResponse<AdminEventSummary>>(`/admin/events?status=${status}&size=100`),
  })
}

export function useAdminEvent(id: string | undefined) {
  return useQuery({ queryKey: keys.event(id ?? ''), queryFn: () => api<EventDetail>(`/admin/events/${id}`), enabled: !!id })
}

function useEventDecision<T>(fn: (vars: T) => Promise<EventDetail>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (event) => {
      qc.setQueryData(keys.event(event.id), event)
      qc.invalidateQueries({ queryKey: ['admin', 'events'] })
    },
  })
}

export function useApproveEvent() {
  return useEventDecision((id: string) => api<EventDetail>(`/admin/events/${id}/approve`, { method: 'POST' }))
}

export function useRejectEvent() {
  return useEventDecision(({ id, reason }: { id: string; reason: string }) =>
    api<EventDetail>(`/admin/events/${id}/reject`, { method: 'POST', json: { reason } }),
  )
}

export function useFeatureEvent() {
  return useEventDecision(({ id, featured }: { id: string; featured: boolean }) =>
    api<EventDetail>(`/admin/events/${id}/feature`, { method: 'POST', json: { featured } }),
  )
}

// ---- Categories ---------------------------------------------------------------------------------

export function useAdminCategories() {
  return useQuery({ queryKey: keys.categories, queryFn: () => api<Category[]>('/admin/categories') })
}

export function useSaveCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: Partial<Omit<Category, 'id'>> }) =>
      id
        ? api<Category>(`/admin/categories/${id}`, { method: 'PATCH', json: input })
        : api<Category>('/admin/categories', { method: 'POST', json: input }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.categories })
      qc.invalidateQueries({ queryKey: ['categories'] })
    },
  })
}
