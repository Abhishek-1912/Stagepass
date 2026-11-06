import { ArrowRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { PublicEventCard } from '@/features/discovery/types'
import { EventCard } from './EventCard'
import { EventCardSkeleton } from './EventCardSkeleton'

/** A titled, horizontally scrolling row of event cards. Renders nothing when empty (unless loading). */
export function EventRail({
  title,
  events,
  loading,
  seeAll,
  action,
}: {
  title: string
  events: PublicEventCard[] | undefined
  loading?: boolean
  seeAll?: string
  action?: ReactNode
}) {
  if (!loading && !events?.length) return null
  return (
    <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6" aria-label={title}>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-2xl font-bold sm:text-3xl">{title}</h2>
        <div className="flex items-center gap-3">
          {action}
          {seeAll && (
            <Link to={seeAll} className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
              See all <ArrowRight className="size-4" />
            </Link>
          )}
        </div>
      </div>
      <div className="-mx-4 flex snap-x gap-4 overflow-x-auto px-4 pb-3 sm:-mx-6 sm:px-6 [scrollbar-width:thin]">
        {loading
          ? Array.from({ length: 4 }, (_, i) => <EventCardSkeleton key={i} className="w-72 shrink-0" />)
          : events!.map((e) => <EventCard key={e.id} event={e} variant="rail" />)}
      </div>
    </section>
  )
}
