import type { LucideIcon } from 'lucide-react'
import { Menu } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { NavLink } from 'react-router'
import { ThemeToggle } from '@/app/theme/ThemeToggle'
import { Logo } from '@/components/brand/Logo'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import { HeaderAccount } from './HeaderAccount'

export type NavItem = { to: string; label: string; icon: LucideIcon; end?: boolean }

function SideNav({ nav, onNavigate }: { nav: NavItem[]; onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-0.5" aria-label="Dashboard">
      {nav.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
              isActive && 'bg-sidebar-accent text-sidebar-accent-foreground',
            )
          }
        >
          <Icon className="size-4" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}

/** Shell for organizer and admin areas: fixed sidebar on desktop, slide-over menu on mobile. */
export function DashboardLayout({ nav, title, children }: { nav: NavItem[]; title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="flex min-h-dvh">
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col gap-6 border-r border-sidebar-border bg-sidebar px-4 py-5 lg:flex">
        <Logo className="px-2" />
        <p className="px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">{title}</p>
        <SideNav nav={nav} />
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border/60 bg-background/80 px-4 backdrop-blur-xl sm:px-6">
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open menu">
                <Menu className="size-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-72 gap-6 p-5">
              <SheetTitle asChild>
                <div>
                  <Logo />
                </div>
              </SheetTitle>
              <SideNav nav={nav} onNavigate={() => setOpen(false)} />
            </SheetContent>
          </Sheet>
          <h1 className="font-sans text-base font-semibold tracking-normal">{title}</h1>
          <div className="ml-auto flex items-center gap-1.5">
            <ThemeToggle />
            <HeaderAccount />
          </div>
        </header>
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  )
}
