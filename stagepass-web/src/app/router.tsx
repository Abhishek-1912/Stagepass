import { createBrowserRouter } from 'react-router'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { AccountPlaceholderPage } from '@/features/account/AccountPlaceholderPage'
import { ForgotPasswordPage } from '@/features/auth/ForgotPasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { ResetPasswordPage } from '@/features/auth/ResetPasswordPage'
import { HomePage } from '@/features/home/HomePage'
import { NotFoundPage } from '@/features/misc/NotFoundPage'
import { BecomeOrganizerPage } from '@/features/organizer/BecomeOrganizerPage'
import { OrganizerHomePage } from '@/features/organizer/OrganizerHomePage'
import { OrganizerLayout } from '@/features/organizer/OrganizerLayout'

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
    children: [{ index: true, element: <OrganizerHomePage /> }],
  },
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <HomePage /> },
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
