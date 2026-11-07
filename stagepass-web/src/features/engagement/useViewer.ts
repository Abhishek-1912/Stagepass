import { useQuery } from '@tanstack/react-query'
import { useOptionalAuth } from '@/features/auth/AuthProvider'
import { meQueryKey, type Me } from '@/features/auth/useMe'
import { api } from '@/lib/api/client'

/** Who is looking: signed in or not, and their roles (empty when signed out). */
export function useViewer(): { signedIn: boolean; roles: string[] } {
  const auth = useOptionalAuth()
  const signedIn = !!auth?.session
  const me = useQuery({ queryKey: meQueryKey, queryFn: () => api<Me>('/me'), enabled: signedIn, staleTime: 60_000 })
  return { signedIn, roles: me.data?.roles ?? [] }
}
