import { useState } from 'react'
import { Button } from '@/components/ui/button'
import type { ExploreFilters, SortOption } from './filters'

const input =
  'h-10 w-full rounded-xl border border-input bg-background px-3 text-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'

/** Price, dates, free-only and sort. Applies on "Show results" so typing doesn't refetch on every key. */
export function FilterPanel({
  filters,
  onApply,
  hasLocation,
}: {
  filters: ExploreFilters
  onApply: (next: ExploreFilters) => void
  hasLocation: boolean
}) {
  const [min, setMin] = useState(filters.minPrice?.toString() ?? '')
  const [max, setMax] = useState(filters.maxPrice?.toString() ?? '')
  const [free, setFree] = useState(!!filters.free)
  const [from, setFrom] = useState(filters.from ?? '')
  const [to, setTo] = useState(filters.to ?? '')
  const [sort, setSort] = useState<SortOption>(filters.sort ?? 'popular')

  const num = (v: string) => (/^\d+$/.test(v.trim()) ? Number(v.trim()) : undefined)

  function apply() {
    onApply({
      ...filters,
      minPrice: free ? undefined : num(min),
      maxPrice: free ? undefined : num(max),
      free: free || undefined,
      from: from || undefined,
      to: to || undefined,
      date: from || to ? undefined : filters.date,
      sort,
    })
  }

  return (
    <div className="space-y-6">
      <fieldset className="space-y-2">
        <legend className="text-sm font-semibold">Price (₹)</legend>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={free} onChange={(e) => setFree(e.target.checked)} />
          Free events only
        </label>
        {!free && (
          <div className="grid grid-cols-2 gap-2">
            <input aria-label="Minimum price" inputMode="numeric" placeholder="Min" className={input} value={min} onChange={(e) => setMin(e.target.value)} />
            <input aria-label="Maximum price" inputMode="numeric" placeholder="Max" className={input} value={max} onChange={(e) => setMax(e.target.value)} />
          </div>
        )}
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-sm font-semibold">Dates</legend>
        <div className="grid grid-cols-2 gap-2">
          <label className="space-y-1 text-xs text-muted-foreground">
            From
            <input type="date" className={input} value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="space-y-1 text-xs text-muted-foreground">
            To
            <input type="date" className={input} value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </label>
        </div>
      </fieldset>

      <label className="block space-y-2">
        <span className="text-sm font-semibold">Sort by</span>
        <select className={input} value={sort} onChange={(e) => setSort(e.target.value as SortOption)}>
          <option value="popular">Most popular</option>
          <option value="soonest">Soonest first</option>
          {hasLocation && <option value="nearby">Nearest first</option>}
        </select>
      </label>

      <Button className="w-full rounded-full" onClick={apply}>
        Show results
      </Button>
    </div>
  )
}
