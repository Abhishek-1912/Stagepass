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

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
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
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
