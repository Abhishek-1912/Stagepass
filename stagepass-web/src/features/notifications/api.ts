import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { PageResponse } from '@/lib/api/types'

export interface AppNotification {
  id: string
  type: string
  title: string
  body: string | null
  link: string | null
  createdAt: string
  read: boolean
}

export function useUnreadCount(enabled = true) {
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ count: number }>('/me/notifications/unread-count'),
    enabled,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })
}

export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api<PageResponse<AppNotification>>('/me/notifications?size=10'),
    enabled,
  })
}

function useNotificationMutation<T>(fn: (v: T) => Promise<unknown>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSettled: () => qc.invalidateQueries({ queryKey: ['notifications'] }) })
}

export function useMarkRead() {
  return useNotificationMutation((id: string) => api<void>(`/me/notifications/${id}/read`, { method: 'POST' }))
}

export function useMarkAllRead() {
  return useNotificationMutation(() => api<void>('/me/notifications/read-all', { method: 'POST' }))
}
