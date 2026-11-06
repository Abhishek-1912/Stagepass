import { MapPin } from 'lucide-react'
import { useCities } from './api'

/** Native select of cities that currently have events, plus "All cities". */
export function CityPicker({
  value,
  onChange,
  className,
}: {
  value: string | null | undefined
  onChange: (city: string | null) => void
  className?: string
}) {
  const { data: cities } = useCities()
  const options = cities ?? []
  const known = !value || options.some((c) => c.name.toLowerCase() === value.toLowerCase())
  return (
    <label className={`relative inline-flex items-center ${className ?? ''}`}>
      <MapPin className="pointer-events-none absolute left-3 size-4 text-muted-foreground" />
      <span className="sr-only">City</span>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        className="h-10 appearance-none rounded-full border border-input bg-background pl-9 pr-8 text-sm font-medium outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
      >
        <option value="">All cities</option>
        {!known && value && <option value={value}>{value}</option>}
        {options.map((c) => (
          <option key={c.name} value={c.name}>
            {c.name} ({c.upcomingEvents})
          </option>
        ))}
      </select>
    </label>
  )
}
