import type { Page, Route } from '@playwright/test'
import type { PublicEventCard } from '../../src/features/discovery/types'
import { publicEvent } from '../../src/features/event/testDetail'
import { testBooking } from '../../src/features/bookings/testBooking'

export const event = publicEvent()

export const card: PublicEventCard = {
  id: event.id,
  slug: event.slug,
  title: event.title,
  summary: event.summary,
  coverImageUrl: null,
  startsAt: event.startsAt,
  endsAt: event.endsAt,
  online: false,
  venueName: 'Vagator Beach',
  city: 'Goa',
  categorySlug: event.categorySlug,
  categoryName: event.categoryName,
  minPricePaise: 49900,
  soldOut: false,
  featured: true,
  distanceKm: null,
}

const price = { subtotalPaise: 49900, feePaise: 2248, feeGstPaise: 405, totalPaise: 52553, commissionPaise: 0, ticketCount: 1 }
const items = [{ ticketTypeId: 't1', name: 'General', quantity: 1, unitPricePaise: 49900, amountPaise: 49900 }]

export const pending = testBooking({
  status: 'PENDING_PAYMENT',
  items,
  price,
  tickets: [],
  holdExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
  checkout: { razorpayKeyId: 'rzp_test_e2e', razorpayOrderId: 'order_e2e', amountPaise: 52553, currency: 'INR', companyName: 'Stagepass' },
})

export const confirmed = testBooking({
  items,
  price,
  tickets: [{ id: 'k1', code: 'SP-AAAA-2222', ticketTypeId: 't1', ticketTypeName: 'General', holderName: 'Asha Rao', status: 'VALID', qrToken: 'e2e.token' }],
})

const page0 = <T>(items: T[]) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 })

const me = {
  id: 'u-e2e',
  email: 'asha@stagepass.test',
  fullName: 'Asha Rao',
  phone: null,
  avatarUrl: null,
  city: 'Goa',
  roles: ['ATTENDEE'],
  emailReminders: true,
}

const summary = {
  id: confirmed.id,
  code: confirmed.code,
  status: 'CONFIRMED',
  eventTitle: event.title,
  eventSlug: event.slug,
  coverImageUrl: null,
  eventStartsAt: event.startsAt,
  city: 'Goa',
  ticketCount: 1,
  totalPaise: price.totalPaise,
}

type Handler = (route: Route, url: URL) => unknown

/** Answers http://api.test/** from fixtures. Unknown calls get a 404 problem so a missing stub is easy to spot. */
export async function fakeApi(page: Page, overrides: Record<string, Handler> = {}) {
  let paid = false
  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

  const handlers: Record<string, Handler> = {
    'GET /api/v1/public/home': (r) => json(r, { featured: [card], trending: [card], thisWeekend: [], upcoming: [card], nearby: [], categories: [] }),
    'GET /api/v1/public/cities': (r) => json(r, [{ name: 'Goa', events: 1 }]),
    'GET /api/v1/public/categories': (r) => json(r, []),
    'GET /api/v1/public/events': (r) => json(r, page0([card])),
    [`GET /api/v1/public/events/${event.slug}`]: (r) => json(r, event),
    [`GET /api/v1/public/events/${event.slug}/reviews`]: (r) => json(r, page0([])),
    'POST /api/v1/public/quote': (r) => json(r, { lines: items, price, holdMinutes: 10 }),
    'GET /api/v1/me': (r) => json(r, me),
    'GET /api/v1/me/saved/ids': (r) => json(r, []),
    'GET /api/v1/me/notifications/unread-count': (r) => json(r, { count: 0 }),
    'GET /api/v1/me/notifications': (r) => json(r, page0([])),
    'GET /api/v1/me/bookings': (r) => json(r, page0([summary])),
    'POST /api/v1/bookings': (r) => json(r, pending, 201),
    'POST /api/v1/payments/verify': (r) => {
      paid = true
      return json(r, confirmed)
    },
    [`GET /api/v1/me/bookings/${confirmed.id}`]: (r) => json(r, paid ? confirmed : confirmed),
    ...overrides,
  }

  await page.route('http://api.test/**', async (route) => {
    const url = new URL(route.request().url())
    const key = `${route.request().method()} ${url.pathname}`
    const handler = handlers[key]
    if (handler) return handler(route, url)
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204 })
    return json(route, { status: 404, code: 'NOT_FOUND', detail: `No e2e stub for ${key}` }, 404)
  })

  // Razorpay Checkout: a stand-in that reports a successful payment.
  await page.route('https://checkout.razorpay.com/**', (route) =>
    route.fulfill({
      contentType: 'application/javascript',
      body: `window.Razorpay = function (opts) {
        return { on: function () {}, open: function () {
          setTimeout(function () { opts.handler({ razorpay_order_id: opts.order_id, razorpay_payment_id: 'pay_e2e', razorpay_signature: 'sig_e2e' }) }, 50)
        } }
      }`,
    }),
  )
  // Any other outside request (images, fonts, Supabase) is answered empty so tests never touch the network.
  await page.route(/^https:\/\/(res\.cloudinary\.com|test\.supabase\.co|fonts\.)/, (route) => route.fulfill({ status: 204 }))
}
