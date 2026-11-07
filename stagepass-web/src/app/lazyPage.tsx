import { lazy, Suspense, type ComponentType } from 'react'
import { PageLoader } from '@/components/feedback/PageLoader'
import { LazyBoundary } from './LazyBoundary'

/** A route element whose code loads on first visit, with a loader and stale-deploy handling. */
export function lazyPage<P extends object>(load: () => Promise<ComponentType<P>>) {
  const Page = lazy(async () => ({ default: await load() }))
  return function LazyRoute(props: P) {
    return (
      <LazyBoundary>
        <Suspense fallback={<PageLoader />}>
          <Page {...props} />
        </Suspense>
      </LazyBoundary>
    )
  }
}
