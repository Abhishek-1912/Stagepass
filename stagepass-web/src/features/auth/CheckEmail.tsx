import { MailCheck } from 'lucide-react'
import type { ReactNode } from 'react'

export function CheckEmail({ email, children }: { email: string; children: ReactNode }) {
  return (
    <div role="status" className="space-y-4 rounded-2xl border border-border bg-card p-6 text-center shadow-[var(--shadow-card)]">
      <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-accent text-accent-foreground">
        <MailCheck className="size-6" />
      </span>
      <p className="font-semibold">Check your inbox</p>
      <p className="text-sm text-muted-foreground">
        We sent a link to <span className="font-medium text-foreground">{email}</span>. {children}
      </p>
    </div>
  )
}
