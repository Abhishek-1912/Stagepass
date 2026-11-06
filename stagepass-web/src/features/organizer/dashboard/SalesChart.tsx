import { useState } from 'react'
import { formatPaiseExact } from '@/lib/money'
import type { DayPoint } from './types'

const W = 600
const H = 200
const PAD = { top: 8, right: 4, bottom: 22, left: 44 }
const PLOT_W = W - PAD.left - PAD.right
const PLOT_H = H - PAD.top - PAD.bottom

const day = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const compactRupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
})

function dayLabel(date: string) {
  return day.format(new Date(`${date}T00:00:00Z`))
}

function tickets(n: number) {
  return `${n} ${n === 1 ? 'ticket' : 'tickets'}`
}

/** Smallest of 1, 2, 5 × 10ⁿ at or above v (at least 1). */
function niceCeil(v: number) {
  if (v <= 1) return 1
  const mag = 10 ** Math.floor(Math.log10(v))
  return [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= v)!
}

/** Bar with 4px rounded top corners and a square base on the baseline. */
function barPath(x: number, y: number, w: number, h: number) {
  const r = Math.min(4, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/**
 * Daily sales as one series of thin bars (no legend: the title names it). Hover or focus a day for its tooltip;
 * a visually hidden table carries the same numbers for screen readers.
 */
export function SalesChart({ points, metric }: { points: DayPoint[]; metric: 'amount' | 'tickets' }) {
  const [active, setActive] = useState<number | null>(null)
  const value = (p: DayPoint) => (metric === 'amount' ? p.amountPaise : p.tickets)
  const total = points.reduce((s, p) => s + value(p), 0)
  const summary =
    metric === 'amount'
      ? `Sales: ${formatPaiseExact(total)} over ${points.length} days`
      : `Tickets sold: ${tickets(total)} over ${points.length} days`

  if (total === 0) {
    return (
      <div className="grid h-52 place-items-center rounded-2xl border border-dashed border-border text-sm text-muted-foreground">
        No sales in this period
      </div>
    )
  }

  const max = niceCeil(Math.max(...points.map(value)))
  const band = PLOT_W / points.length
  const barW = Math.max(2, Math.min(24, band - 2))
  const fractions = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max)
  // Ticket counts are whole numbers: round and drop repeats (max 2 gives 0, 1, 2, not 0, 0, 1, 1, 2).
  const ticks = metric === 'tickets' ? [...new Set(fractions.map(Math.round))] : fractions
  const axisLabel = (v: number) => (metric === 'amount' ? compactRupees.format(v / 100) : String(Math.round(v)))
  const xLabelAt = new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])
  const hovered = active === null ? null : points[active]

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="h-52 w-full" role="img" aria-label={summary} onMouseLeave={() => setActive(null)}>
        {ticks.map((t) => {
          const y = PAD.top + PLOT_H - (t / max) * PLOT_H
          return (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y} y2={y} className="stroke-border" strokeWidth={1} />
              <text data-axis-label x={PAD.left - 6} y={y + 3} textAnchor="end" className="fill-muted-foreground text-[10px]">
                {axisLabel(t)}
              </text>
            </g>
          )
        })}
        {points.map((p, i) => {
          const h = (value(p) / max) * PLOT_H
          const x = PAD.left + i * band + (band - barW) / 2
          const y = PAD.top + PLOT_H - h
          return (
            <g key={p.date}>
              {h > 0 && (
                <path
                  data-bar
                  d={barPath(x, y, barW, h)}
                  className={active === i ? 'fill-primary' : 'fill-primary/85'}
                >
                  <title>{`${dayLabel(p.date)}: ${tickets(p.tickets)} · ${formatPaiseExact(p.amountPaise)}`}</title>
                </path>
              )}
              <rect
                data-hit
                x={PAD.left + i * band}
                y={PAD.top}
                width={band}
                height={PLOT_H}
                fill="transparent"
                tabIndex={0}
                aria-label={`${dayLabel(p.date)}: ${tickets(p.tickets)}, ${formatPaiseExact(p.amountPaise)}`}
                onMouseEnter={() => setActive(i)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
              />
              {xLabelAt.has(i) && (
                <text x={PAD.left + i * band + band / 2} y={H - 6} textAnchor="middle" className="fill-muted-foreground text-[10px]">
                  {dayLabel(p.date)}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      {hovered && active !== null && (
        <div
          role="tooltip"
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg border border-border bg-popover px-3 py-2 text-xs shadow-md"
          style={{ left: `${((PAD.left + active * band + band / 2) / W) * 100}%` }}
        >
          <p className="font-semibold text-foreground">{dayLabel(hovered.date)}</p>
          <p className="text-muted-foreground">
            {tickets(hovered.tickets)} · {formatPaiseExact(hovered.amountPaise)}
          </p>
        </div>
      )}
      <table className="sr-only" aria-label="Sales by day">
        <thead>
          <tr>
            <th>Date</th>
            <th>Tickets</th>
            <th>Sales</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.date}>
              <td>{dayLabel(p.date)}</td>
              <td>{p.tickets}</td>
              <td>{formatPaiseExact(p.amountPaise)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
