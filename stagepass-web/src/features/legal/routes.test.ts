import { expect, test } from 'vitest'
import { router } from '@/app/router'

function paths(routes: typeof router.routes, prefix = ''): string[] {
  return routes.flatMap((r) => {
    const here = r.path ? (r.path.startsWith('/') ? r.path : `${prefix.replace(/\/$/, '')}/${r.path}`) : prefix
    return [here, ...(r.children ? paths(r.children, here || prefix) : [])]
  })
}

test('every footer legal link has a route', () => {
  const all = paths(router.routes)
  for (const p of ['/about', '/legal/terms', '/legal/privacy', '/legal/refunds']) expect(all).toContain(p)
})
