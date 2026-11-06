import { CalendarDays, LayoutDashboard, MapPin, ScanLine, Settings, Wallet } from 'lucide-react'
import { Outlet } from 'react-router'
import { DashboardLayout, type NavItem } from '@/components/layout/DashboardLayout'
import { RequireRole } from '@/features/auth/RequireRole'

const nav: NavItem[] = [
  { to: '/organizer', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/organizer/events', label: 'Events', icon: CalendarDays },
  { to: '/organizer/venues', label: 'Venues', icon: MapPin },
  { to: '/organizer/payouts', label: 'Payouts', icon: Wallet },
  { to: '/organizer/settings', label: 'Settings', icon: Settings },
  { to: '/organizer/scanner', label: 'Scanner (soon)', icon: ScanLine },
]

export function OrganizerLayout() {
  return (
    <RequireRole roles={['ORGANIZER']}>
      <DashboardLayout nav={nav} title="Organizer">
        <Outlet />
      </DashboardLayout>
    </RequireRole>
  )
}
