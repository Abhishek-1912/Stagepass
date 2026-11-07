import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useOptionalAuth } from '@/features/auth/AuthProvider'
import { api } from '@/lib/api/client'

const idsKey = ['saved-ids'] as const

/** Ids of the signed-in user's saved events (empty when signed out). */
export function useSavedIds() {
  const auth = useOptionalAuth()
  return useQuery({
    queryKey: idsKey,
    queryFn: () => api<string[]>('/me/saved/ids'),
    enabled: !!auth?.session,
    staleTime: 60_000,
  })
}

/** Saves or unsaves an event, updating hearts at once and undoing the change if the request fails. */
export function useToggleSaved() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ eventId, save }: { eventId: string; save: boolean }) =>
      api<void>(`/me/saved/${eventId}`, { method: save ? 'PUT' : 'DELETE' }),
    onMutate: async ({ eventId, save }) => {
      await qc.cancelQueries({ queryKey: idsKey })
      const before = qc.getQueryData<string[]>(idsKey) ?? []
      qc.setQueryData<string[]>(idsKey, save ? [eventId, ...before.filter((id) => id !== eventId)] : before.filter((id) => id !== eventId))
      return { before }
    },
    onError: (_e, _vars, ctx) => {
      if (ctx) qc.setQueryData(idsKey, ctx.before)
      toast.error('Could not update your saved events. Try again.')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['saved-events'] }),
  })
}
