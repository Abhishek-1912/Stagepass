import { Outlet } from 'react-router'
import { MobileTabBar } from './MobileTabBar'
import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

export function PublicLayout() {
  return (
    <div className="flex min-h-dvh flex-col pb-16 md:pb-0">
      <SiteHeader />
      <main className="flex-1">
        <Outlet />
      </main>
      <SiteFooter />
      <MobileTabBar />
    </div>
  )
}
