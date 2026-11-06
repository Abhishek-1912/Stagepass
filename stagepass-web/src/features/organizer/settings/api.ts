import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export interface OrganizationSettings {
  orgName: string
  slug: string
  bio: string | null
  logoUrl: string | null
  website: string | null
  contactEmail: string | null
  contactPhone: string | null
  pan: string | null
  gstin: string | null
  payoutAccountName: string | null
  payoutAccountMasked: string | null
  payoutIfsc: string | null
}

export type SettingsUpdate = Partial<{
  bio: string
  logoUrl: string
  website: string
  contactEmail: string
  contactPhone: string
  payoutAccountName: string
  payoutAccountNumber: string
  payoutIfsc: string
}>

const key = ['organizer', 'profile'] as const

export function useOrganizationSettings() {
  return useQuery({ queryKey: key, queryFn: () => api<OrganizationSettings>('/organizer/profile') })
}

export function useUpdateOrganizationSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (update: SettingsUpdate) => api<OrganizationSettings>('/organizer/profile', { method: 'PATCH', json: update }),
    onSuccess: (s) => queryClient.setQueryData(key, s),
  })
}
