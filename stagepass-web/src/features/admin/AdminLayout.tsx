import { BarChart3, CalendarCheck, LayoutDashboard, ReceiptIndianRupee, Store, Tags, Users } from 'lucide-react'
import { Outlet } from 'react-router'
import { DashboardLayout, type NavItem } from '@/components/layout/DashboardLayout'
import { RequireRole } from '@/features/auth/RequireRole'

const nav: NavItem[] = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/admin/organizers', label: 'Organizers', icon: Store },
  { to: '/admin/events', label: 'Event review', icon: CalendarCheck },
  { to: '/admin/categories', label: 'Categories', icon: Tags },
  { to: '/admin/users', label: 'Users (soon)', icon: Users },
  { to: '/admin/transactions', label: 'Transactions (soon)', icon: ReceiptIndianRupee },
  { to: '/admin/analytics', label: 'Analytics (soon)', icon: BarChart3 },
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
