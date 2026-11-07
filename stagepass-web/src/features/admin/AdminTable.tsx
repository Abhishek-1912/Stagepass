import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Shared look for admin list tables, plus empty/error rows. */
export function AdminTable({ head, children }: { head: ReactNode; children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-border bg-card">
      <table className="w-full text-sm">
        <thead className="bg-muted/50 text-left text-xs text-muted-foreground">
          <tr>{head}</tr>
        </thead>
        <tbody className="divide-y divide-border">{children}</tbody>
      </table>
    </div>
  )
}

export function Th({ children, right }: { children?: ReactNode; right?: boolean }) {
  return <th className={cn('px-3 py-2 font-medium whitespace-nowrap', right && 'text-right')}>{children}</th>
}

export function Td({ children, right, className }: { children?: ReactNode; right?: boolean; className?: string }) {
  return <td className={cn('px-3 py-2 align-top', right && 'text-right tabular-nums', className)}>{children}</td>
}

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-2xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">{children}</div>
  )
}

export function ErrorState({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-6 text-sm text-destructive">{children}</div>
}

export function StatusPill({ tone, children }: { tone: 'good' | 'warn' | 'bad' | 'muted'; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        tone === 'good' && 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400',
        tone === 'warn' && 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
        tone === 'bad' && 'bg-destructive/10 text-destructive',
        tone === 'muted' && 'bg-muted text-muted-foreground',
      )}
    >
      {children}
    </span>
  )
}
