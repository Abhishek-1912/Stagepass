import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { useAuth } from './AuthProvider'

export type Role = 'ATTENDEE' | 'ORGANIZER' | 'ADMIN'

export interface Me {
  id: string
  email: string | null
  fullName: string | null
  phone: string | null
  avatarUrl: string | null
  city: string | null
  roles: Role[]
  emailReminders: boolean
}

export type ProfileUpdate = Partial<Pick<Me, 'fullName' | 'phone' | 'city' | 'avatarUrl' | 'emailReminders'>>

export const meQueryKey = ['me'] as const

export function useMe() {
  const { session } = useAuth()
  return useQuery({
    queryKey: meQueryKey,
    queryFn: () => api<Me>('/me'),
    enabled: !!session,
    staleTime: 60_000,
  })
}

export function useUpdateMe() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (update: ProfileUpdate) => api<Me>('/me', { method: 'PATCH', json: update }),
    onSuccess: (me) => queryClient.setQueryData(meQueryKey, me),
  })
}
