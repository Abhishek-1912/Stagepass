import { CalendarCheck, Flag, LayoutDashboard, ReceiptIndianRupee, ScrollText, Settings, Store, Tags, Users, Wallet } from 'lucide-react'
import { Outlet } from 'react-router'
import { DashboardLayout, type NavItem } from '@/components/layout/DashboardLayout'
import { RequireRole } from '@/features/auth/RequireRole'

const nav: NavItem[] = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/organizers', label: 'Organizers', icon: Store },
  { to: '/admin/events', label: 'Event review', icon: CalendarCheck },
  { to: '/admin/transactions', label: 'Transactions', icon: ReceiptIndianRupee },
  { to: '/admin/payouts', label: 'Payouts', icon: Wallet },
  { to: '/admin/reports', label: 'Reports', icon: Flag },
  { to: '/admin/categories', label: 'Categories', icon: Tags },
  { to: '/admin/settings', label: 'Settings', icon: Settings },
  { to: '/admin/audit', label: 'Audit log', icon: ScrollText },
]

export function AdminLayout() {
  return (
    <RequireRole roles={['ADMIN']}>
      <DashboardLayout nav={nav} title="Admin">
        <Outlet />
      </DashboardLayout>
    </RequireRole>
  )
}
