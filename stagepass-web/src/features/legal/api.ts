import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export interface CompanyInfo {
  name: string
  address: string | null
  gstin: string | null
  state: string | null
  email: string | null
  phone: string | null
}

/** The company's public details (same source as GST invoices). Pages render their text without waiting for this. */
export function useCompany() {
  return useQuery({
    queryKey: ['public', 'company'],
    queryFn: () => api<CompanyInfo>('/public/company'),
    staleTime: 60 * 60_000,
    retry: 1,
  })
}
