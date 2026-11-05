import { PageLoader } from '@/components/feedback/PageLoader'
import { Badge } from '@/components/ui/badge'
import { useMe } from '@/features/auth/useMe'
import { isApiError } from '@/lib/api/ApiError'

/** Temporary account page proving the login → API round trip. The full profile arrives in Plan 09. */
export function AccountPlaceholderPage() {
  const { data: me, isPending, error } = useMe()

  if (isPending) return <PageLoader />
  if (error) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">
          {isApiError(error) ? error.detail : 'We couldn’t load your account.'}
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-6 px-4 py-16">
      <h1 className="text-3xl font-bold">Hi {me.fullName ?? 'there'} 👋</h1>
      <dl className="grid grid-cols-[8rem_1fr] gap-y-3 rounded-2xl border border-border bg-card p-6 text-sm shadow-[var(--shadow-card)]">
        <dt className="text-muted-foreground">Email</dt>
        <dd>{me.email ?? '—'}</dd>
        <dt className="text-muted-foreground">Phone</dt>
        <dd>{me.phone ?? '—'}</dd>
        <dt className="text-muted-foreground">City</dt>
        <dd>{me.city ?? '—'}</dd>
        <dt className="text-muted-foreground">Roles</dt>
        <dd className="flex flex-wrap gap-1.5">
          {me.roles.map((r) => (
            <Badge key={r} variant="secondary">
              {r.toLowerCase()}
            </Badge>
          ))}
        </dd>
      </dl>
    </div>
  )
}
