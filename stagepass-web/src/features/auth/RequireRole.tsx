import { ShieldX } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import { type Role, useMe } from './useMe'

/**
 * Shows children only when the signed-in user has at least one of `roles`.
 * This mirrors, and never replaces, the API's own authorization.
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { data: me, isPending, isError, error } = useMe()

  if (isPending) return <PageLoader />
  if (isApiError(error) && error.code === 'ACCOUNT_SUSPENDED') {
    return (
      <Forbidden
        title="Your account has been suspended"
        message="You can’t book or manage events while it’s suspended."
        supportLink
      />
    )
  }
  if (isApiError(error) && error.code === 'ACCOUNT_CONFLICT') {
    return (
      <Forbidden
        title="This email is already linked to another Stagepass account"
        message="Sign in with the account you used before, or contact support to merge them."
        supportLink
      />
    )
  }
  if (isError || !me) {
    return (
      <Forbidden
        title="We couldn’t load your account"
        message="Please refresh the page. If this keeps happening, sign out and back in."
      />
    )
  }
  if (!roles.some((r) => me.roles.includes(r))) {
    return <Forbidden title="You don’t have access to this page" message="This area is for a different account type." />
  }
  return <>{children}</>
}

function Forbidden({ title, message, supportLink }: { title: string; message: string; supportLink?: boolean }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-24 text-center">
      <span className="grid size-12 place-items-center rounded-2xl bg-destructive/10 text-destructive">
        <ShieldX className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="text-muted-foreground">{message}</p>
      {supportLink && (
        <Button asChild className="rounded-full">
          <Link to="/about#contact">Contact support</Link>
        </Button>
      )}
      <Button asChild variant="outline" className="rounded-full">
        <Link to="/">Back to home</Link>
      </Button>
    </div>
  )
}
