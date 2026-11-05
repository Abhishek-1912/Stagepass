import { Link } from 'react-router'
import { Logo } from '@/components/brand/Logo'

const YEAR = new Date().getFullYear()

const columns = [
  {
    title: 'Discover',
    links: [
      { to: '/explore', label: 'All events' },
      { to: '/explore?date=weekend', label: 'This weekend' },
      { to: '/explore?price=free', label: 'Free events' },
    ],
  },
  {
    title: 'Organizers',
    links: [
      { to: '/organizers', label: 'Sell tickets' },
      { to: '/become-organizer', label: 'Become an organizer' },
    ],
  },
  {
    title: 'Company',
    links: [
      { to: '/about', label: 'About & contact' },
      { to: '/legal/terms', label: 'Terms' },
      { to: '/legal/privacy', label: 'Privacy' },
      { to: '/legal/refunds', label: 'Refunds & cancellation' },
    ],
  },
]

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-border/60 bg-card/40">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[1.4fr_repeat(3,1fr)]">
        <div className="space-y-3">
          <Logo />
          <p className="max-w-xs text-sm text-muted-foreground">
            Find the nights you’ll talk about for years. Tickets for concerts, comedy, tech, food and more.
          </p>
        </div>
        {columns.map((c) => (
          <div key={c.title}>
            <h3 className="mb-3 font-sans text-sm font-semibold tracking-normal">{c.title}</h3>
            <ul className="space-y-2">
              {c.links.map((l) => (
                <li key={l.to}>
                  <Link to={l.to} className="text-sm text-muted-foreground transition-colors hover:text-foreground">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-border/60 py-6 text-center text-xs text-muted-foreground">
        © {YEAR} Stagepass. All rights reserved.
      </div>
    </footer>
  )
}
