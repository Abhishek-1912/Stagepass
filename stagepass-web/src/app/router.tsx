import { createBrowserRouter } from 'react-router'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { AccountPlaceholderPage } from '@/features/account/AccountPlaceholderPage'
import { AdminHomePage } from '@/features/admin/AdminHomePage'
import { AdminLayout } from '@/features/admin/AdminLayout'
import { CategoriesPage } from '@/features/admin/CategoriesPage'
import { EventReviewDetail } from '@/features/admin/EventReviewDetail'
import { EventReviewPage } from '@/features/admin/EventReviewPage'
import { OrganizerApplicationsPage } from '@/features/admin/OrganizerApplicationsPage'
import { ForgotPasswordPage } from '@/features/auth/ForgotPasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { ResetPasswordPage } from '@/features/auth/ResetPasswordPage'
import { ExplorePage } from '@/features/discovery/ExplorePage'
import { HomePage } from '@/features/home/HomePage'
import { NotFoundPage } from '@/features/misc/NotFoundPage'
import { BecomeOrganizerPage } from '@/features/organizer/BecomeOrganizerPage'
import { OrganizerHomePage } from '@/features/organizer/OrganizerHomePage'
import { OrganizerLayout } from '@/features/organizer/OrganizerLayout'
import { EventEditorPage } from '@/features/organizer/events/EventEditorPage'
import { EventsListPage } from '@/features/organizer/events/EventsListPage'
import { VenuesPage } from '@/features/organizer/venues/VenuesPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
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
      { path: 'events/:id/:step?', element: <EventEditorPage /> },
      { path: 'venues', element: <VenuesPage /> },
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
      { index: true, element: <AdminHomePage /> },
      { path: 'organizers', element: <OrganizerApplicationsPage /> },
      { path: 'events', element: <EventReviewPage /> },
      { path: 'events/:id', element: <EventReviewDetail /> },
      { path: 'categories', element: <CategoriesPage /> },
    ],
  },
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'explore', element: <ExplorePage /> },
      {
        path: 'account',
        element: (
          <RequireAuth>
            <AccountPlaceholderPage />
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
