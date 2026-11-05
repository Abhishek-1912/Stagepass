import { Link } from 'react-router'
import { Button } from '@/components/ui/button'

/** Replaced in Task 6 by the signed-in avatar menu. */
export function HeaderAccount() {
  return (
    <Button asChild size="sm" className="rounded-full px-4">
      <Link to="/login">Sign in</Link>
    </Button>
  )
}
