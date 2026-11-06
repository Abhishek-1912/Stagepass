export type DatePreset = 'today' | 'tomorrow' | 'weekend' | 'week' | 'month'
export type SortOption = 'popular' | 'soonest' | 'nearby'

export interface ExploreFilters {
  q?: string
  city?: string
  category?: string
  date?: DatePreset
  from?: string
  to?: string
  minPrice?: number
  maxPrice?: number
  free?: boolean
  sort?: SortOption
  lat?: number
  lng?: number
}

const DATE_PRESETS: DatePreset[] = ['today', 'tomorrow', 'weekend', 'week', 'month']
const SORTS: SortOption[] = ['popular', 'soonest', 'nearby']
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

function int(v: string | null): number | undefined {
  if (v === null || !/^\d+$/.test(v)) return undefined
  return Number(v)
}

function coord(v: string | null, limit: number): number | undefined {
  if (v === null) return undefined
  const n = Number(v)
  return Number.isFinite(n) && Math.abs(n) <= limit ? n : undefined
}

/** Reads Explore filters from the URL, ignoring anything invalid. */
export function parseFilters(search: URLSearchParams): ExploreFilters {
  const f: ExploreFilters = {}
  const q = search.get('q')?.trim()
  if (q) f.q = q.slice(0, 100)
  const city = search.get('city')?.trim()
  if (city) f.city = city
  const category = search.get('category')?.trim()
  if (category && /^[a-z0-9-]+$/.test(category)) f.category = category
  const date = search.get('date') as DatePreset | null
  if (date && DATE_PRESETS.includes(date)) f.date = date
  const from = search.get('from')
  if (from && ISO_DATE.test(from)) f.from = from
  const to = search.get('to')
  if (to && ISO_DATE.test(to)) f.to = to
  f.minPrice = int(search.get('minPrice'))
  f.maxPrice = int(search.get('maxPrice'))
  if (search.get('free') === 'true' || search.get('price') === 'free') f.free = true
  const sort = search.get('sort') as SortOption | null
  if (sort && SORTS.includes(sort)) f.sort = sort
  f.lat = coord(search.get('lat'), 90)
  f.lng = coord(search.get('lng'), 180)
  if (f.sort === 'nearby' && (f.lat === undefined || f.lng === undefined)) delete f.sort
  return Object.fromEntries(Object.entries(f).filter(([, v]) => v !== undefined)) as ExploreFilters
}

/** Writes filters to URL params, omitting empty and default values. */
export function toSearchParams(f: ExploreFilters): URLSearchParams {
  const p = new URLSearchParams()
  if (f.q) p.set('q', f.q)
  if (f.city) p.set('city', f.city)
  if (f.category) p.set('category', f.category)
  if (f.date) p.set('date', f.date)
  if (f.from) p.set('from', f.from)
  if (f.to) p.set('to', f.to)
  if (f.minPrice !== undefined) p.set('minPrice', String(f.minPrice))
  if (f.maxPrice !== undefined) p.set('maxPrice', String(f.maxPrice))
  if (f.free) p.set('free', 'true')
  if (f.sort && f.sort !== 'popular') p.set('sort', f.sort)
  if (f.lat !== undefined && f.lng !== undefined) {
    p.set('lat', String(f.lat))
    p.set('lng', String(f.lng))
  }
  return p
}

export function activeFilterCount(f: ExploreFilters): number {
  return [f.category, f.date ?? f.from ?? f.to, f.minPrice ?? f.maxPrice, f.free].filter(Boolean).length
}
