import { expect, test } from '@playwright/test'
import { event, fakeApi } from './fixtures/api'
import { signIn } from './fixtures/session'

const pages = ['/', '/explore', `/events/${event.slug}`, '/account', '/account/bookings']

test('pages fit a phone screen', async ({ page }, info) => {
  test.skip(info.project.name !== 'phone', 'phone layout only')
  await signIn(page)
  await fakeApi(page)

  for (const path of pages) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, `${path} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0)
    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible()
  }
})
