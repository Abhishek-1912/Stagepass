import { Link } from 'react-router'
import { cn } from '@/lib/utils'

export function Logo({ className }: { className?: string }) {
  return (
    <Link to="/" className={cn('flex items-center gap-2 font-display text-lg font-bold tracking-tight', className)}>
      <span
        aria-hidden
        className="bg-gradient-brand grid size-8 place-items-center rounded-[10px] text-sm text-white shadow-[0_6px_16px_-6px_rgb(108_76_241/0.7)]"
      >
        S
      </span>
      Stagepass
    </Link>
  )
}
