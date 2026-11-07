import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'
import type { CheckInStats, Gate, ScanLogRow, ScannerEvent, ScanResponse } from './types'

export function useScannerEvents() {
  return useQuery({ queryKey: ['checkin', 'events'], queryFn: () => api<ScannerEvent[]>('/checkin/events') })
}

export function useCheckInStats(eventId: string, { poll = false } = {}) {
  return useQuery({
    queryKey: ['checkin', 'stats', eventId],
    queryFn: () => api<CheckInStats>(`/checkin/events/${eventId}/stats`),
    refetchInterval: poll ? 10_000 : false,
  })
}

export type ScanInput = { eventId: string; gateId: string | null } & ({ token: string } | { code: string })

export function scanTicket(input: ScanInput) {
  return api<ScanResponse>('/checkin/scan', { method: 'POST', json: input })
}

export function useGates(eventId: string) {
  return useQuery({ queryKey: ['checkin', 'gates', eventId], queryFn: () => api<Gate[]>(`/organizer/events/${eventId}/gates`) })
}

function useGateMutation<T>(eventId: string, call: (arg: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: call,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['checkin', 'gates', eventId] })
      void queryClient.invalidateQueries({ queryKey: ['checkin', 'stats', eventId] })
    },
  })
}

export interface GateInput {
  name: string
  ticketTypeIds: string[]
}

export function useCreateGate(eventId: string) {
  return useGateMutation(eventId, (g: GateInput) => api<Gate>(`/organizer/events/${eventId}/gates`, { method: 'POST', json: g }))
}

export function useUpdateGate(eventId: string) {
  return useGateMutation(eventId, ({ id, ...g }: GateInput & { id: string }) =>
    api<Gate>(`/organizer/events/${eventId}/gates/${id}`, { method: 'PATCH', json: g }),
  )
}

export function useDeleteGate(eventId: string) {
  return useGateMutation(eventId, (id: string) => api<void>(`/organizer/events/${eventId}/gates/${id}`, { method: 'DELETE' }))
}

export function useScanLog(eventId: string, { result, page }: { result: string; page: number }) {
  return useQuery({
    queryKey: ['checkin', 'scan-log', eventId, result, page],
    queryFn: () =>
      api<PageResponse<ScanLogRow>>(`/organizer/events/${eventId}/scan-log?page=${page}&size=20${result ? `&result=${result}` : ''}`),
    placeholderData: keepPreviousData,
    refetchInterval: 10_000,
  })
}
