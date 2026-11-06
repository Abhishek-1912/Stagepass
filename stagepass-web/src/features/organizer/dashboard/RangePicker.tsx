import { cn } from '@/lib/utils'
import type { Range } from './types'

const RANGES: Range[] = [7, 30, 90]

/** Segmented 7 / 30 / 90 day control. */
export function RangePicker({ value, onChange }: { value: Range; onChange: (r: Range) => void }) {
  return (
    <div role="radiogroup" aria-label="Date range" className="inline-flex gap-1 rounded-full border border-border p-1">
      {RANGES.map((r) => (
        <button
          key={r}
          type="button"
          role="radio"
          aria-checked={value === r}
          aria-label={`${r} days`}
          onClick={() => onChange(r)}
          className={cn(
            'rounded-full px-3 py-1 text-xs font-medium transition',
            value === r ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {r}d
        </button>
      ))}
    </div>
  )
}
