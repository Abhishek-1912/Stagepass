import { expect, test } from '@playwright/test'
import { confirmed, event, fakeApi } from './fixtures/api'
import { testCsp } from './fixtures/csp'
import { signIn } from './fixtures/session'

const pages = ['/', '/explore', `/events/${event.slug}`, '/about', '/legal/refunds', `/account/bookings/${confirmed.id}/tickets/k1`]

test('the production build runs under the enforced CSP', async ({ page, baseURL }) => {
  const csp = testCsp()
  const violations: string[] = []
  page.on('console', (m) => /Refused to|Content Security Policy/i.test(m.text()) && violations.push(m.text()))

  await signIn(page)
  await page.addInitScript(() => {
    localStorage.setItem('stagepass-theme', 'dark')
    document.addEventListener('securitypolicyviolation', (e) => {
      const w = window as unknown as { __csp?: string[] }
      ;(w.__csp ??= []).push(`${e.effectiveDirective} ${e.blockedURI}`)
    })
  })
  await fakeApi(page)
  // Serve every page of the production build with the exact enforced header.
  await page.route(`${baseURL}/**`, async (route) => {
    if (route.request().resourceType() !== 'document') return route.fallback()
    const response = await route.fetch()
    await route.fulfill({ response, headers: { ...response.headers(), 'content-security-policy': csp } })
  })

  for (const path of pages) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    const blocked = await page.evaluate(() => (window as unknown as { __csp?: string[] }).__csp ?? [])
    expect([...blocked, ...violations], `${path} under the enforced CSP`).toEqual([])
    // The inline theme script ran (its hash is allowed).
    await expect(page.locator('html')).toHaveClass(/dark/)
  }
  await expect(page.getByRole('img', { name: /QR code for SP-AAAA-2222/ })).toBeVisible()
})
