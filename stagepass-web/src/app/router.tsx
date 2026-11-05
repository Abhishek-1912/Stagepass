import { createBrowserRouter } from 'react-router'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { HomePage } from '@/features/home/HomePage'
import { NotFoundPage } from '@/features/misc/NotFoundPage'

export const router = createBrowserRouter([
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
