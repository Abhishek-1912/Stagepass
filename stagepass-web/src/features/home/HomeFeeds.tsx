import { CalendarHeart, LocateFixed, Loader2 } from 'lucide-react'
import { Link } from 'react-router'
import { EventCard } from '@/components/events/EventCard'
import { EventCardSkeleton } from '@/components/events/EventCardSkeleton'
import { EventRail } from '@/components/events/EventRail'
import { Button } from '@/components/ui/button'
import { usePublicHome } from '@/features/discovery/api'
import { CityPicker } from '@/features/discovery/CityPicker'
import { useLocationPreference } from '@/features/discovery/useLocationPreference'

/** Live homepage sections. Each one disappears when it has nothing to show. */
export function HomeFeeds() {
  const location = useLocationPreference()
  const { data, isPending, isError, refetch } = usePublicHome({
    city: location.city,
    lat: location.coords?.lat,
    lng: location.coords?.lng,
  })
  const cityQuery = location.city ? `&city=${encodeURIComponent(location.city)}` : ''
  const nothing =
    !isPending &&
    data &&
    !data.featured.length &&
    !data.upcoming.length &&
    !data.trending.length &&
    !data.categories.length

  const nearbyAction = (
    <div className="flex flex-wrap items-center gap-2">
      <CityPicker value={location.city} onChange={location.setCity} />
      <Button
        variant="outline"
        size="sm"
        className="h-10 rounded-full"
        onClick={() => location.requestCoords()}
        disabled={location.status === 'locating'}
      >
        {location.status === 'locating' ? <Loader2 className="animate-spin" /> : <LocateFixed />}
        {location.status === 'granted' ? 'Using your location' : 'Use my location'}
      </Button>
    </div>
  )

  if (isError) {
    return (
      <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6">
        <div role="alert" className="flex flex-wrap items-center justify-between gap-4 rounded-3xl border border-border bg-card p-6">
          <p className="text-muted-foreground">We couldn’t load events right now.</p>
          <Button variant="outline" className="rounded-full" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      </section>
    )
  }

  return (
    <>
      {(isPending || !!data?.featured.length) && (
        <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6" aria-label="Featured">
          <h2 className="mb-5 text-2xl font-bold sm:text-3xl">Featured</h2>
          <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {isPending
              ? Array.from({ length: 3 }, (_, i) => <EventCardSkeleton key={i} />)
              : data!.featured.map((e) => <EventCard key={e.id} event={e} variant="large" />)}
          </div>
        </section>
      )}

      <EventRail title="This weekend" events={data?.thisWeekend} seeAll={`/explore?date=weekend${cityQuery}`} />
      <EventRail title="Trending" events={data?.trending} loading={isPending} seeAll={`/explore?sort=popular${cityQuery}`} />

      {location.status === 'denied' && (
        <p role="status" className="mx-auto mt-10 max-w-7xl px-4 text-sm text-muted-foreground sm:px-6">
          We couldn’t get your location. Pick a city instead to see events near you.
        </p>
      )}
      {data?.nearby.length ? (
        <EventRail
          title={location.coords ? 'Near you' : `In ${location.city}`}
          events={data.nearby}
          action={nearbyAction}
          seeAll={location.coords ? `/explore?sort=nearby&lat=${location.coords.lat}&lng=${location.coords.lng}` : `/explore?city=${encodeURIComponent(location.city ?? '')}`}
        />
      ) : (
        !isPending && (
          <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6" aria-label="Near you">
            <div className="flex flex-wrap items-center justify-between gap-4 rounded-3xl border border-dashed border-border p-6">
              <div>
                <h2 className="font-sans text-lg font-semibold tracking-normal">Find events near you</h2>
                <p className="text-sm text-muted-foreground">Choose your city or share your location.</p>
              </div>
              {nearbyAction}
            </div>
          </section>
        )
      )}

      <EventRail title="Upcoming" events={data?.upcoming} seeAll={`/explore?sort=soonest${cityQuery}`} />

      {data?.categories.map((rail) => (
        <EventRail key={rail.slug} title={rail.name} events={rail.events} seeAll={`/explore?category=${rail.slug}${cityQuery}`} />
      ))}

      {nothing && (
        <section className="mx-auto max-w-7xl px-4 pt-14 sm:px-6">
          <div className="flex flex-col items-center gap-3 rounded-3xl border border-border bg-card p-10 text-center">
            <CalendarHeart className="size-8 text-primary" />
            <h2 className="text-xl font-bold">New events are on the way</h2>
            <p className="max-w-md text-muted-foreground">
              {location.city ? `Nothing is live in ${location.city} yet. ` : ''}Check back soon, or explore everything on
              Stagepass.
            </p>
            <Button asChild className="rounded-full">
              <Link to="/explore">Explore all events</Link>
            </Button>
          </div>
        </section>
      )}
    </>
  )
}
