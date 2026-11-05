import { NavLink } from 'react-router'
import { ThemeToggle } from '@/app/theme/ThemeToggle'
import { Logo } from '@/components/brand/Logo'
import { cn } from '@/lib/utils'
import { HeaderAccount } from './HeaderAccount'

const links = [
  { to: '/explore', label: 'Explore' },
  { to: '/organizers', label: 'For organizers' },
]

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/75 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Logo />
        <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              className={({ isActive }) =>
                cn(
                  'rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground',
                  isActive && 'text-foreground',
                )
              }
            >
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-1.5">
          <ThemeToggle />
          <HeaderAccount />
        </div>
      </div>
    </header>
  )
}
