import { expect, test } from '@playwright/test'
import { event, fakeApi } from './fixtures/api'
import { signIn } from './fixtures/session'

test('browse → event → checkout → ticket', async ({ page }) => {
  const problems: string[] = []
  page.on('console', (m) => m.type() === 'error' && problems.push(`console: ${m.text()}`))
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`))
  page.on('response', (r) => r.status() >= 400 && problems.push(`${r.status()} ${r.request().method()} ${r.url()}`))
  await signIn(page)
  await fakeApi(page)

  await page.goto('/')
  await page.getByRole('link', { name: new RegExp(event.title) }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: event.title })).toBeVisible()

  await page.getByRole('button', { name: 'Add General' }).click()
  await page.getByRole('button', { name: /^Continue/ }).click()

  await expect(page).toHaveURL(/\/checkout\//)
  try {
    await page.getByLabel('Full name').waitFor({ timeout: 20_000 })
  } catch {
    const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ').slice(0, 600)
    throw new Error(`Checkout form did not appear at ${page.url()}. Page says: ${text}. Problems: ${problems.join(' | ')}`)
  }
  await page.getByLabel('Full name').fill('Asha Rao')
  await page.getByLabel('Email').fill('asha@stagepass.test')
  await page.getByRole('button', { name: /^Pay / }).click()

  await expect(page).toHaveURL(/\/account\/bookings\/b1/)
  await expect(page.getByText('SP-ABC234').first()).toBeVisible()
  await page.getByRole('link', { name: /SP-AAAA-2222|view ticket|show ticket/i }).first().click()
  await expect(page.getByRole('img', { name: /SP-AAAA-2222|qr/i }).first()).toBeVisible()
})

test('signed-out checkout asks to sign in', async ({ page }) => {
  await fakeApi(page)
  await page.goto(`/events/${event.slug}`)
  await page.getByRole('button', { name: 'Add General' }).click()
  await page.getByRole('button', { name: /^Continue/ }).click()
  await expect(page).toHaveURL(/\/login\?next=/)
})
