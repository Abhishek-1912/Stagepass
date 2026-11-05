import { motion } from 'motion/react'
import {
  Briefcase,
  Cpu,
  Hammer,
  Laugh,
  MapPin,
  MicVocal,
  Music,
  PartyPopper,
  Search,
  Trophy,
  Utensils,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { placeholderImages } from '@/lib/images'

const categories = [
  { slug: 'music', name: 'Music', icon: Music },
  { slug: 'concerts', name: 'Concerts', icon: MicVocal },
  { slug: 'technology', name: 'Technology', icon: Cpu },
  { slug: 'business', name: 'Business', icon: Briefcase },
  { slug: 'workshops', name: 'Workshops', icon: Hammer },
  { slug: 'sports', name: 'Sports', icon: Trophy },
  { slug: 'comedy', name: 'Comedy', icon: Laugh },
  { slug: 'festivals', name: 'Festivals', icon: PartyPopper },
  { slug: 'food-lifestyle', name: 'Food & Lifestyle', icon: Utensils },
]

function HeroSearch() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [city, setCity] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    const params = new URLSearchParams()
    if (q.trim()) params.set('q', q.trim())
    if (city.trim()) params.set('city', city.trim())
    navigate(`/explore${params.size ? `?${params}` : ''}`)
  }

  return (
    <form
      onSubmit={submit}
      role="search"
      className="flex w-full max-w-2xl flex-col gap-2 rounded-2xl border border-white/15 bg-white/10 p-2 shadow-2xl backdrop-blur-xl sm:flex-row sm:items-center sm:rounded-full"
    >
      <label className="flex flex-1 items-center gap-2 rounded-full px-4 py-2.5 text-white">
        <Search className="size-4 shrink-0 opacity-70" />
        <span className="sr-only">Search events</span>
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Artists, events, venues"
          className="w-full bg-transparent text-sm placeholder:text-white/60 focus:outline-none"
        />
      </label>
      <label className="flex items-center gap-2 rounded-full px-4 py-2.5 text-white sm:w-48 sm:border-l sm:border-white/15">
        <MapPin className="size-4 shrink-0 opacity-70" />
        <span className="sr-only">City</span>
        <input
          value={city}
          onChange={(e) => setCity(e.target.value)}
          placeholder="Any city"
          className="w-full bg-transparent text-sm placeholder:text-white/60 focus:outline-none"
        />
      </label>
      <Button type="submit" size="lg" className="rounded-full px-6">
        Find events
      </Button>
    </form>
  )
}

export function HomePage() {
  return (
    <>
      <section className="relative isolate overflow-hidden">
        <img
          src={placeholderImages.heroConcert}
          alt=""
          className="absolute inset-0 -z-20 size-full object-cover"
          fetchPriority="high"
        />
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top_left,rgb(108_76_241/0.85),transparent_60%),linear-gradient(to_bottom,rgb(11_12_16/0.45),rgb(11_12_16/0.92))]" />
        <div className="mx-auto flex min-h-[560px] max-w-7xl flex-col justify-end gap-8 px-4 pb-16 pt-28 sm:px-6 md:min-h-[640px] md:pb-24">
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
            className="max-w-3xl space-y-5"
          >
            <span className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-xs font-medium text-white backdrop-blur">
              <span className="size-1.5 rounded-full bg-brand-coral" /> Live this week across India
            </span>
            <h1 className="text-4xl font-bold leading-[1.05] text-white sm:text-6xl md:text-7xl">
              Your next favourite night out starts here.
            </h1>
            <p className="max-w-xl text-base text-white/75 sm:text-lg">
              Concerts, comedy, tech meetups and food festivals. Book in seconds and walk in with a QR ticket.
            </p>
          </motion.div>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
          >
            <HeroSearch />
          </motion.div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6" aria-labelledby="categories-heading">
        <div className="mb-6 flex items-end justify-between">
          <h2 id="categories-heading" className="text-2xl font-bold sm:text-3xl">
            Browse by vibe
          </h2>
          <Link to="/explore" className="text-sm font-medium text-primary hover:underline">
            See all events
          </Link>
        </div>
        <ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-3 sm:px-0 lg:grid-cols-9">
          {categories.map(({ slug, name, icon: Icon }) => (
            <li key={slug} className="snap-start">
              <Link
                to={`/explore?category=${slug}`}
                className="group flex w-28 flex-col items-center gap-3 rounded-2xl border border-border bg-card p-4 text-center shadow-[var(--shadow-card)] transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-[var(--shadow-card-hover)] sm:w-auto"
              >
                <span className="grid size-11 place-items-center rounded-xl bg-accent text-accent-foreground transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                  <Icon className="size-5" />
                </span>
                <span className="text-xs font-medium">{name}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto max-w-7xl px-4 pt-16 sm:px-6">
        <div className="bg-gradient-sunset relative overflow-hidden rounded-3xl px-6 py-12 text-white sm:px-12">
          <div className="max-w-xl space-y-4">
            <h2 className="text-3xl font-bold sm:text-4xl">Hosting something? Sell out on Stagepass.</h2>
            <p className="text-white/80">
              Create an event in minutes, set ticket tiers, get paid securely, and check guests in with your phone.
            </p>
            <Button asChild size="lg" variant="secondary" className="rounded-full">
              <Link to="/become-organizer">Start selling — it’s free</Link>
            </Button>
          </div>
          <img
            src={placeholderImages.festivalCrowd}
            alt=""
            loading="lazy"
            className="absolute -right-10 top-1/2 hidden h-[130%] w-1/2 -translate-y-1/2 rotate-3 rounded-3xl object-cover opacity-90 shadow-2xl lg:block"
          />
        </div>
      </section>
    </>
  )
}
