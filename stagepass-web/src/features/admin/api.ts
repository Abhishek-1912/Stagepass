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
import type {
  AdminDisputeRow,
  AdminOrganizerDetail,
  AdminOrganizerRow,
  AdminOverview,
  AdminPaymentRow,
  AdminRefundRow,
  AdminReportRow,
  AdminUser,
  AuditRow,
  FeeSettings,
  OrganizerPayoutSummary,
  OrganizerUpdate,
  PayoutRow,
  RecordPayoutInput,
  ReportStatus,
} from './types'

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

// ---- Plan 8: platform admin ---------------------------------------------------------------------

/** `{a: 1, b: undefined, c: ''}` → `a=1` (empty values are left out). */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  }
  const s = q.toString()
  return s ? `?${s}` : ''
}

function useAdminMutation<T, R>(fn: (vars: T) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: ['admin'] }) })
}

export function useAdminOverview(days: number) {
  return useQuery({ queryKey: ['admin', 'overview', days], queryFn: () => api<AdminOverview>(`/admin/overview?days=${days}`) })
}

export interface UserFilters {
  q?: string
  role?: string
  status?: string
  page?: number
}

export function useAdminUsers(f: UserFilters) {
  return useQuery({
    queryKey: ['admin', 'users', f],
    queryFn: () => api<PageResponse<AdminUser>>(`/admin/users${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
}

export function useSuspendUser() {
  return useAdminMutation(({ id, reason }: { id: string; reason: string }) =>
    api<AdminUser>(`/admin/users/${id}/suspend`, { method: 'POST', json: { reason } }),
  )
}

export function useReactivateUser() {
  return useAdminMutation((id: string) => api<AdminUser>(`/admin/users/${id}/reactivate`, { method: 'POST' }))
}

export function useSetAdmin() {
  return useAdminMutation(({ id, admin }: { id: string; admin: boolean }) =>
    api<AdminUser>(`/admin/users/${id}/roles/ADMIN`, { method: admin ? 'PUT' : 'DELETE' }),
  )
}

export function useAdminOrganizers(f: { q?: string; status?: string; page?: number }) {
  return useQuery({
    queryKey: ['admin', 'organizers', f],
    queryFn: () => api<PageResponse<AdminOrganizerRow>>(`/admin/organizers${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
}

export function useAdminOrganizer(id: string | undefined) {
  return useQuery({
    queryKey: ['admin', 'organizer', id],
    queryFn: () => api<AdminOrganizerDetail>(`/admin/organizers/${id}`),
    enabled: !!id,
  })
}

export function useSuspendOrganizer() {
  return useAdminMutation(({ id, reason }: { id: string; reason: string }) =>
    api<AdminOrganizerDetail>(`/admin/organizers/${id}/suspend`, { method: 'POST', json: { reason } }),
  )
}

export function useReinstateOrganizer() {
  return useAdminMutation((id: string) => api<AdminOrganizerDetail>(`/admin/organizers/${id}/reinstate`, { method: 'POST' }))
}

export function useUpdateOrganizer() {
  return useAdminMutation(({ id, update }: { id: string; update: OrganizerUpdate }) =>
    api<AdminOrganizerDetail>(`/admin/organizers/${id}`, { method: 'PATCH', json: update }),
  )
}

export function useCancelEvent() {
  return useAdminMutation(({ id, reason }: { id: string; reason: string }) =>
    api<EventDetail>(`/admin/events/${id}/cancel`, { method: 'POST', json: { reason } }),
  )
}

export function useAdminEventSearch(status: EventStatus, q: string) {
  return useQuery({
    queryKey: ['admin', 'events', status, q],
    queryFn: () => api<PageResponse<AdminEventSummary>>(`/admin/events${query({ status, q, size: 100 })}`),
    placeholderData: (prev) => prev,
  })
}

export interface PaymentFilters {
  q?: string
  status?: string
  from?: string
  to?: string
  page?: number
}

export function useAdminPayments(f: PaymentFilters) {
  return useQuery({
    queryKey: ['admin', 'payments', f],
    queryFn: () => api<PageResponse<AdminPaymentRow>>(`/admin/payments${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
}

export function useAdminRefunds(f: { q?: string; status?: string; page?: number }) {
  return useQuery({
    queryKey: ['admin', 'refunds', f],
    queryFn: () => api<PageResponse<AdminRefundRow>>(`/admin/refunds${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
}

export function useAdminDisputes(f: { status?: string; page?: number }) {
  return useQuery({
    queryKey: ['admin', 'disputes', f],
    queryFn: () => api<PageResponse<AdminDisputeRow>>(`/admin/disputes${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
}

export function useAdminRefund() {
  return useAdminMutation(
    ({ bookingId, includeFee, reason }: { bookingId: string; includeFee: boolean; reason: string }) =>
      api<unknown>(`/admin/bookings/${bookingId}/refund`, { method: 'POST', json: { ticketIds: [], includeFee, reason } }),
  )
}

export function useRetryRefund() {
  return useAdminMutation((id: string) => api<AdminRefundRow>(`/admin/refunds/${id}/retry`, { method: 'POST' }))
}

export function useAdminPayouts(f: { q?: string; onlyPayable?: boolean; page?: number }) {
  return useQuery({
    queryKey: ['admin', 'payouts', f],
    queryFn: () => api<PageResponse<OrganizerPayoutSummary>>(`/admin/payouts${query({ ...f, onlyPayable: f.onlyPayable || undefined })}`),
    placeholderData: (prev) => prev,
  })
}

export function useRecordPayout() {
  return useAdminMutation(({ organizerId, input }: { organizerId: string; input: RecordPayoutInput }) =>
    api<PayoutRow>(`/admin/organizers/${organizerId}/payouts`, { method: 'POST', json: input }),
  )
}

export function useFeeSettings() {
  return useQuery({ queryKey: ['admin', 'settings', 'fees'], queryFn: () => api<FeeSettings>('/admin/settings/fees') })
}

export function useSaveFeeSettings() {
  return useAdminMutation((input: FeeSettings) => api<FeeSettings>('/admin/settings/fees', { method: 'PUT', json: input }))
}

export function useAdminReports(status: ReportStatus, page = 0) {
  return useQuery({
    queryKey: ['admin', 'reports', status, page],
    queryFn: () => api<PageResponse<AdminReportRow>>(`/admin/reports${query({ status, page })}`),
    placeholderData: (prev) => prev,
  })
}

export function useDecideReport() {
  return useAdminMutation(({ id, action, note }: { id: string; action: 'resolve' | 'dismiss'; note: string }) =>
    api<unknown>(`/admin/reports/${id}/${action}`, { method: 'POST', json: { note } }),
  )
}

export interface AuditFilters {
  actor?: string
  action?: string
  entityType?: string
  entityId?: string
  from?: string
  to?: string
  page?: number
}

export function useAuditLog(f: AuditFilters) {
  return useQuery({
    queryKey: ['admin', 'audit', f],
    queryFn: () => api<PageResponse<AuditRow>>(`/admin/audit${query({ ...f })}`),
    placeholderData: (prev) => prev,
  })
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
