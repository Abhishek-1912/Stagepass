import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export interface StaffMember {
  id: string
  email: string
  /** Set once the person has signed in. */
  name?: string
  /** Missing = all of the organizer's events. */
  eventId?: string
  eventTitle?: string
  status: 'ACTIVE' | 'REMOVED'
  createdAt: string
  acceptedAt?: string
}

const key = ['organizer', 'staff'] as const

export function useStaff() {
  return useQuery({ queryKey: key, queryFn: () => api<StaffMember[]>('/organizer/staff') })
}

export function useInviteStaff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; eventId: string | null }) => api<StaffMember>('/organizer/staff', { method: 'POST', json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}

export function useRemoveStaff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/organizer/staff/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}
