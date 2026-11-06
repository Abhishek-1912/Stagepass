import { ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/** Search box that reports its value 300 ms after typing stops. */
export function SearchBox({ placeholder, onSearch }: { placeholder: string; onSearch: (q: string) => void }) {
  const [value, setValue] = useState('')
  const latest = useRef(onSearch)
  const first = useRef(true)

  useEffect(() => {
    latest.current = onSearch
  }, [onSearch])

  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    const id = setTimeout(() => latest.current(value), 300)
    return () => clearTimeout(id)
  }, [value])

  return (
    <div className="relative w-full max-w-xs">
      <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="pl-9"
      />
    </div>
  )
}

export function StatusFilter({ value, options, onChange }: { value: string; options: { value: string; label: string }[]; onChange: (v: string) => void }) {
  return (
    <select
      aria-label="Status"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="h-9 rounded-md border border-input bg-background px-3 text-sm"
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}

export function Pager({ page, totalPages, totalItems, onPage }: { page: number; totalPages: number; totalItems: number; onPage: (p: number) => void }) {
  if (totalItems === 0) return null
  return (
    <div className="flex items-center justify-between gap-3 text-sm text-muted-foreground">
      <span>
        {totalItems} {totalItems === 1 ? 'result' : 'results'}
      </span>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="icon" className="size-8 rounded-full" aria-label="Previous page" disabled={page === 0} onClick={() => onPage(page - 1)}>
          <ChevronLeft className="size-4" />
        </Button>
        <span>
          Page {page + 1} of {Math.max(1, totalPages)}
        </span>
        <Button variant="outline" size="icon" className="size-8 rounded-full" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}
