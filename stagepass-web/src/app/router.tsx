import { createBrowserRouter } from 'react-router'
import { lazyPage } from './lazyPage'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { LoginPage } from '@/features/auth/LoginPage'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { ExplorePage } from '@/features/discovery/ExplorePage'
import { EventPage } from '@/features/event/EventPage'
import { HomePage } from '@/features/home/HomePage'
import { OrganizerProfilePage } from '@/features/organizer-public/OrganizerProfilePage'
import { NotFoundPage } from '@/features/misc/NotFoundPage'

const AccountPage = lazyPage(() => import('@/features/account/AccountPage').then((m) => m.AccountPage))
const SavedEventsPage = lazyPage(() => import('@/features/account/SavedEventsPage').then((m) => m.SavedEventsPage))
const AdminLayout = lazyPage(() => import('@/features/admin/AdminLayout').then((m) => m.AdminLayout))
const AdminPayoutsPage = lazyPage(() => import('@/features/admin/AdminPayoutsPage').then((m) => m.AdminPayoutsPage))
const AuditLogPage = lazyPage(() => import('@/features/admin/AuditLogPage').then((m) => m.AuditLogPage))
const CategoriesPage = lazyPage(() => import('@/features/admin/CategoriesPage').then((m) => m.CategoriesPage))
const EventReviewDetail = lazyPage(() => import('@/features/admin/EventReviewDetail').then((m) => m.EventReviewDetail))
const EventReviewPage = lazyPage(() => import('@/features/admin/EventReviewPage').then((m) => m.EventReviewPage))
const OrganizerDetailPage = lazyPage(() => import('@/features/admin/OrganizerDetailPage').then((m) => m.OrganizerDetailPage))
const OrganizersPage = lazyPage(() => import('@/features/admin/OrganizersPage').then((m) => m.OrganizersPage))
const OverviewPage = lazyPage(() => import('@/features/admin/OverviewPage').then((m) => m.OverviewPage))
const ReportsPage = lazyPage(() => import('@/features/admin/ReportsPage').then((m) => m.ReportsPage))
const SettingsPage = lazyPage(() => import('@/features/admin/SettingsPage').then((m) => m.SettingsPage))
const TransactionsPage = lazyPage(() => import('@/features/admin/TransactionsPage').then((m) => m.TransactionsPage))
const UsersPage = lazyPage(() => import('@/features/admin/UsersPage').then((m) => m.UsersPage))
const ForgotPasswordPage = lazyPage(() => import('@/features/auth/ForgotPasswordPage').then((m) => m.ForgotPasswordPage))
const RegisterPage = lazyPage(() => import('@/features/auth/RegisterPage').then((m) => m.RegisterPage))
const ResetPasswordPage = lazyPage(() => import('@/features/auth/ResetPasswordPage').then((m) => m.ResetPasswordPage))
const BookingDetailPage = lazyPage(() => import('@/features/bookings/BookingDetailPage').then((m) => m.BookingDetailPage))
const MyBookingsPage = lazyPage(() => import('@/features/bookings/MyBookingsPage').then((m) => m.MyBookingsPage))
const TicketViewPage = lazyPage(() => import('@/features/bookings/TicketViewPage').then((m) => m.TicketViewPage))
const CheckoutPage = lazyPage(() => import('@/features/checkout/CheckoutPage').then((m) => m.CheckoutPage))
const ResumeCheckoutPage = lazyPage(() => import('@/features/checkout/CheckoutPage').then((m) => m.ResumeCheckoutPage))
const ScannerHomePage = lazyPage(() => import('@/features/checkin/ScannerHomePage').then((m) => m.ScannerHomePage))
const ScannerPage = lazyPage(() => import('@/features/checkin/ScannerPage').then((m) => m.ScannerPage))
const BecomeOrganizerPage = lazyPage(() => import('@/features/organizer/BecomeOrganizerPage').then((m) => m.BecomeOrganizerPage))
const OrganizerHomePage = lazyPage(() => import('@/features/organizer/OrganizerHomePage').then((m) => m.OrganizerHomePage))
const OrganizerLayout = lazyPage(() => import('@/features/organizer/OrganizerLayout').then((m) => m.OrganizerLayout))
const EventEditorPage = lazyPage(() => import('@/features/organizer/events/EventEditorPage').then((m) => m.EventEditorPage))
const EventManagePage = lazyPage(() => import('@/features/organizer/manage/EventManagePage').then((m) => m.EventManagePage))
const PayoutsPage = lazyPage(() => import('@/features/organizer/payouts/PayoutsPage').then((m) => m.PayoutsPage))
const StaffPage = lazyPage(() => import('@/features/organizer/staff/StaffPage').then((m) => m.StaffPage))
const OrganizationSettingsPage = lazyPage(() => import('@/features/organizer/settings/OrganizationSettingsPage').then((m) => m.OrganizationSettingsPage))
const EventsListPage = lazyPage(() => import('@/features/organizer/events/EventsListPage').then((m) => m.EventsListPage))
const VenuesPage = lazyPage(() => import('@/features/organizer/venues/VenuesPage').then((m) => m.VenuesPage))

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    path: '/scan',
    element: (
      <RequireAuth>
        <ScannerHomePage />
      </RequireAuth>
    ),
  },
  {
    path: '/scan/:eventId',
    element: (
      <RequireAuth>
        <ScannerPage />
      </RequireAuth>
    ),
  },
  {
    path: '/organizer',
    element: (
      <RequireAuth>
        <OrganizerLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <OrganizerHomePage /> },
      { path: 'events', element: <EventsListPage /> },
      { path: 'events/new', element: <EventEditorPage /> },
      { path: 'events/:id/manage', element: <EventManagePage /> },
      { path: 'events/:id/:step?', element: <EventEditorPage /> },
      { path: 'venues', element: <VenuesPage /> },
      { path: 'staff', element: <StaffPage /> },
      { path: 'payouts', element: <PayoutsPage /> },
      { path: 'settings', element: <OrganizationSettingsPage /> },
    ],
  },
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <OverviewPage /> },
      { path: 'users', element: <UsersPage /> },
      { path: 'organizers', element: <OrganizersPage /> },
      { path: 'organizers/:id', element: <OrganizerDetailPage /> },
      { path: 'events', element: <EventReviewPage /> },
      { path: 'events/:id', element: <EventReviewDetail /> },
      { path: 'categories', element: <CategoriesPage /> },
      { path: 'transactions', element: <TransactionsPage /> },
      { path: 'payouts', element: <AdminPayoutsPage /> },
      { path: 'reports', element: <ReportsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'audit', element: <AuditLogPage /> },
    ],
  },
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'explore', element: <ExplorePage /> },
      { path: 'events/:slug', element: <EventPage /> },
      { path: 'o/:slug', element: <OrganizerProfilePage /> },
      {
        path: 'checkout/booking/:bookingId',
        element: (
          <RequireAuth>
            <ResumeCheckoutPage />
          </RequireAuth>
        ),
      },
      {
        path: 'checkout/:slug',
        element: (
          <RequireAuth>
            <CheckoutPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/bookings',
        element: (
          <RequireAuth>
            <MyBookingsPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/bookings/:id',
        element: (
          <RequireAuth>
            <BookingDetailPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/bookings/:id/tickets/:ticketId',
        element: (
          <RequireAuth>
            <TicketViewPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account',
        element: (
          <RequireAuth>
            <AccountPage />
          </RequireAuth>
        ),
      },
      {
        path: 'account/saved',
        element: (
          <RequireAuth>
            <SavedEventsPage />
          </RequireAuth>
        ),
      },
      {
        path: 'become-organizer',
        element: (
          <RequireAuth>
            <BecomeOrganizerPage />
          </RequireAuth>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
