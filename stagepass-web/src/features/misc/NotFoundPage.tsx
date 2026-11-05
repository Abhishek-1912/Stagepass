import { Link } from 'react-router'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-32 text-center">
      <p className="text-gradient-brand font-display text-7xl font-bold">404</p>
      <h1 className="text-2xl font-bold">This page left the venue</h1>
      <p className="text-muted-foreground">The link may be broken or the event may have ended.</p>
      <Button asChild className="rounded-full">
        <Link to="/explore">Explore events</Link>
      </Button>
    </div>
  )
}
