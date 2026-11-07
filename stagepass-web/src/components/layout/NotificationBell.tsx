import { Bell } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from '@/features/notifications/api'
import { cn } from '@/lib/utils'

const ago = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

function when(iso: string): string {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60_000)
  if (Math.abs(minutes) < 60) return ago.format(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return ago.format(hours, 'hour')
  return ago.format(Math.round(hours / 24), 'day')
}

/** Bell with the unread count (refreshed every minute) and the 10 latest notifications. */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const unread = useUnreadCount()
  const list = useNotifications(open)
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()
  const count = unread.data?.count ?? 0
  const items = list.data?.items ?? []

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
          className="relative grid size-9 place-items-center rounded-full text-muted-foreground outline-none ring-ring/50 hover:text-foreground focus-visible:ring-2"
        >
          <Bell className="size-5" aria-hidden />
          {count > 0 && (
            <span className="absolute -right-0.5 -top-0.5 grid min-w-4.5 place-items-center rounded-full bg-primary px-1 text-[10px] font-bold leading-4.5 text-primary-foreground">
              {count > 9 ? '9+' : count}
            </span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel className="flex items-center justify-between">
          <span>Notifications</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {list.isPending && open ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">Loading…</p>
        ) : items.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-muted-foreground">You're all caught up</p>
        ) : (
          items.map((n) => (
            <DropdownMenuItem
              key={n.id}
              className="flex flex-col items-start gap-0.5 py-2"
              onSelect={() => {
                if (!n.read) markRead.mutate(n.id)
                if (n.link) navigate(n.link)
              }}
            >
              <span className={cn('text-sm', !n.read && 'font-semibold')}>{n.title}</span>
              {n.body && <span className="line-clamp-2 text-xs text-muted-foreground">{n.body}</span>}
              <span className="text-[11px] text-muted-foreground">{when(n.createdAt)}</span>
            </DropdownMenuItem>
          ))
        )}
        {count > 0 && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => markAll.mutate(undefined)} className="justify-center text-sm font-medium text-primary">
              Mark all as read
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
