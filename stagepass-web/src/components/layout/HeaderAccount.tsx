import { Heart, LayoutDashboard, LogOut, Shield, Ticket, User } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuth } from '@/features/auth/AuthProvider'
import { useMe } from '@/features/auth/useMe'

function initials(name: string | null | undefined, email: string | null | undefined) {
  const source = name?.trim() || email || '?'
  return source
    .split(/[\s@]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')
}

export function HeaderAccount() {
  const { session, loading, signOut } = useAuth()
  const { data: me } = useMe()
  const navigate = useNavigate()

  if (loading) return <div className="size-8" aria-hidden />
  if (!session) {
    return (
      <Button asChild size="sm" className="rounded-full px-4">
        <Link to="/login">Sign in</Link>
      </Button>
    )
  }

  const roles = me?.roles ?? []
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="rounded-full outline-none ring-ring/50 focus-visible:ring-2" aria-label="Account menu">
          <Avatar className="size-8">
            {me?.avatarUrl && <AvatarImage src={me.avatarUrl} alt="" />}
            <AvatarFallback className="bg-gradient-brand text-xs font-semibold text-white">
              {initials(me?.fullName, me?.email ?? session.user.email)}
            </AvatarFallback>
          </Avatar>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="truncate font-normal">
          <span className="block text-sm font-medium">{me?.fullName ?? 'Your account'}</span>
          <span className="block truncate text-xs text-muted-foreground">{me?.email ?? session.user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/account/bookings">
            <Ticket /> My tickets
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/account/saved">
            <Heart /> Saved events
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/account">
            <User /> Profile
          </Link>
        </DropdownMenuItem>
        {roles.includes('ORGANIZER') && (
          <DropdownMenuItem asChild>
            <Link to="/organizer">
              <LayoutDashboard /> Organizer dashboard
            </Link>
          </DropdownMenuItem>
        )}
        {roles.includes('ADMIN') && (
          <DropdownMenuItem asChild>
            <Link to="/admin">
              <Shield /> Admin
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onSelect={async () => {
            await signOut()
            navigate('/', { replace: true })
          }}
        >
          <LogOut /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
