import { Search, SlidersHorizontal } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { EventCard } from '@/components/events/EventCard'
import { EventCardSkeleton } from '@/components/events/EventCardSkeleton'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useCategories } from '@/features/organizer/api'
import { cn } from '@/lib/utils'
import { usePublicEvents } from './api'
import { CityPicker } from './CityPicker'
import { FilterPanel } from './FilterPanel'
import { activeFilterCount, type DatePreset, type ExploreFilters, parseFilters, toSearchParams } from './filters'

const DATE_CHIPS: { value: DatePreset; label: string }[] = [
  { value: 'today', label: 'Today' },
  { value: 'tomorrow', label: 'Tomorrow' },
  { value: 'weekend', label: 'This weekend' },
  { value: 'week', label: 'This week' },
  { value: 'month', label: 'This month' },
]

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        'shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors',
        active ? 'border-foreground bg-foreground text-background' : 'border-border bg-card text-muted-foreground hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}

export function ExplorePage() {
  const [params, setParams] = useSearchParams()
  const filters = useMemo(() => parseFilters(params), [params])
  const [q, setQ] = useState(filters.q ?? '')
  // Keep the box in sync when the URL changes from outside (Back/Forward, links, Clear filters).
  const [syncedQ, setSyncedQ] = useState(filters.q)
  if (filters.q !== syncedQ) {
    setSyncedQ(filters.q)
    setQ(filters.q ?? '')
  }
  const [sheetOpen, setSheetOpen] = useState(false)
  const { data: categories } = useCategories()
  const query = usePublicEvents(filters)

  const update = (next: ExploreFilters) => setParams(toSearchParams(next))

  // The live URL params, so the debounced search below never writes back a stale copy of the other filters
  // (React Router's functional setter sees the params from when the timer was created).
  const latestParams = useRef(params)
  useEffect(() => {
    latestParams.current = params
  }, [params])

  // Debounce typing into the URL; replace the history entry so each pause in typing doesn't add a Back step.
  useEffect(() => {
    const trimmed = q.trim()
    if (trimmed === (filters.q ?? '')) return
    const t = setTimeout(
      () => setParams(toSearchParams({ ...parseFilters(latestParams.current), q: trimmed || undefined }), { replace: true }),
      300,
    )
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q])

  useEffect(() => {
    document.title = 'Explore events · Stagepass'
  }, [])

  const events = query.data?.pages.flatMap((p) => p.items) ?? []
  const total = query.data?.pages[0]?.totalItems ?? 0
  const filterCount = activeFilterCount(filters)
  const hasLocation = filters.lat !== undefined && filters.lng !== undefined

  const panel = (
    <FilterPanel
      key={params.toString()}
      filters={filters}
      hasLocation={hasLocation}
      onApply={(next) => {
        update(next)
        setSheetOpen(false)
      }}
    />
  )

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="sticky top-16 z-20 -mx-4 space-y-3 bg-background/90 px-4 pb-3 pt-2 backdrop-blur-xl sm:-mx-6 sm:px-6">
        <div className="flex flex-wrap items-center gap-2">
          <label className="relative flex min-w-56 flex-1 items-center">
            <Search className="pointer-events-none absolute left-3 size-4 text-muted-foreground" />
            <span className="sr-only">Search events</span>
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search artists, events, venues"
              className="h-11 w-full rounded-full border border-input bg-card pl-10 pr-4 text-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
            />
          </label>
          <CityPicker value={filters.city} onChange={(city) => update({ ...filters, city: city ?? undefined })} />
          <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
            <SheetTrigger asChild>
              <Button variant="outline" className="h-10 rounded-full lg:hidden">
                <SlidersHorizontal /> Filters{filterCount ? ` (${filterCount})` : ''}
              </Button>
            </SheetTrigger>
            <SheetContent side="bottom" className="max-h-[85dvh] overflow-y-auto rounded-t-3xl">
              <SheetHeader>
                <SheetTitle>Filters</SheetTitle>
              </SheetHeader>
              <div className="px-4 pb-6">{panel}</div>
            </SheetContent>
          </Sheet>
        </div>
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6">
          {DATE_CHIPS.map((c) => (
            <Chip
              key={c.value}
              active={filters.date === c.value}
              onClick={() =>
                update({ ...filters, date: filters.date === c.value ? undefined : c.value, from: undefined, to: undefined })
              }
            >
              {c.label}
            </Chip>
          ))}
          <span className="mx-1 w-px shrink-0 bg-border" aria-hidden />
          {categories?.map((c) => (
            <Chip
              key={c.slug}
              active={filters.category === c.slug}
              onClick={() => update({ ...filters, category: filters.category === c.slug ? undefined : c.slug })}
            >
              {c.name}
            </Chip>
          ))}
        </div>
      </div>

      <div className="mt-6 grid gap-8 lg:grid-cols-[260px_1fr]">
        <aside className="hidden lg:block">
          <div className="sticky top-48 rounded-2xl border border-border bg-card p-5">{panel}</div>
        </aside>

        <section aria-live="polite">
          <div className="mb-4 flex items-center justify-between">
            <h1 className="font-sans text-base font-semibold tracking-normal">
              {query.isPending ? 'Finding events…' : `${total} ${total === 1 ? 'event' : 'events'}`}
              {filters.city ? ` in ${filters.city}` : ''}
            </h1>
          </div>

          {query.isPending ? (
            <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: 6 }, (_, i) => (
                <EventCardSkeleton key={i} />
              ))}
            </div>
          ) : query.isError ? (
            <div role="alert" className="rounded-2xl border border-border bg-card p-8 text-center">
              <p className="text-muted-foreground">We couldn’t load events right now.</p>
              <Button variant="outline" className="mt-3 rounded-full" onClick={() => query.refetch()}>
                Try again
              </Button>
            </div>
          ) : events.length ? (
            <>
              <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
                {events.map((e) => (
                  <EventCard key={e.id} event={e} />
                ))}
              </div>
              {query.hasNextPage && (
                <div className="mt-8 flex justify-center">
                  <Button variant="outline" className="rounded-full px-6" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>
                    {query.isFetchingNextPage ? 'Loading…' : 'Load more'}
                  </Button>
                </div>
              )}
            </>
          ) : (
            <div className="rounded-3xl border border-dashed border-border p-12 text-center">
              <p className="font-semibold">No events match these filters</p>
              <p className="mt-1 text-sm text-muted-foreground">Try another date or city, or clear everything.</p>
              <Button
                variant="outline"
                className="mt-4 rounded-full"
                onClick={() => {
                  setQ('')
                  update({})
                }}
              >
                Clear filters
              </Button>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
