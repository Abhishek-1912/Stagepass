import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { CheckoutDismissed, loadRazorpay, openRazorpay, resetRazorpayLoader } from './razorpay'

const info = { razorpayKeyId: 'rzp_test_key', razorpayOrderId: 'order_1', amountPaise: 105104, currency: 'INR', companyName: 'Stagepass' }

beforeEach(() => {
  resetRazorpayLoader()
  delete window.Razorpay
  document.head.querySelectorAll('script').forEach((s) => s.remove())
})
afterEach(() => vi.useRealTimers())

function scripts() {
  return document.head.querySelectorAll('script[src="https://checkout.razorpay.com/v1/checkout.js"]')
}

test('loadRazorpay injects the script once', async () => {
  const a = loadRazorpay()
  const b = loadRazorpay()
  expect(scripts()).toHaveLength(1)

  const Ctor = vi.fn()
  window.Razorpay = Ctor as never
  scripts()[0].dispatchEvent(new Event('load'))

  await expect(a).resolves.toBe(Ctor)
  await expect(b).resolves.toBe(Ctor)
  await expect(loadRazorpay()).resolves.toBe(Ctor)
  expect(scripts()).toHaveLength(1)
})

test('loadRazorpay gives up after the timeout and can retry', async () => {
  vi.useFakeTimers()
  const p = loadRazorpay(1000)
  vi.advanceTimersByTime(1000)
  await expect(p).rejects.toThrow(/too long/)
  expect(scripts()).toHaveLength(0)
  void loadRazorpay(1000).catch(() => {})
  expect(scripts()).toHaveLength(1)
})

type Options = { handler: (r: unknown) => void; modal: { ondismiss: () => void }; order_id: string; key: string; amount: number }

function fakeRazorpay() {
  const state: { options?: Options; failed?: (r: unknown) => void } = {}
  window.Razorpay = class {
    constructor(options: Record<string, unknown>) {
      state.options = options as unknown as Options
    }
    open() {}
    on(_e: string, cb: (r: unknown) => void) {
      state.failed = cb
    }
  } as never
  return state
}

test('openRazorpay resolves with the ids to verify', async () => {
  const rzp = fakeRazorpay()
  const p = openRazorpay(info, { name: 'Asha' }, 'Sunburn')
  await vi.waitFor(() => expect(rzp.options).toBeDefined())
  expect(rzp.options).toMatchObject({ key: 'rzp_test_key', order_id: 'order_1', amount: 105104 })

  rzp.options!.handler({ razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' })
  await expect(p).resolves.toEqual({ razorpayOrderId: 'order_1', razorpayPaymentId: 'pay_1', razorpaySignature: 'sig' })
})

test('closing the window rejects as dismissed, with the last failure if any', async () => {
  const rzp = fakeRazorpay()
  const p = openRazorpay(info, {}, 'Sunburn')
  await vi.waitFor(() => expect(rzp.options).toBeDefined())
  rzp.failed!({ error: { description: 'Card declined by bank' } })
  rzp.options!.modal.ondismiss()

  const err = await p.catch((e) => e)
  expect(err).toBeInstanceOf(CheckoutDismissed)
  expect(err.lastFailure).toBe('Card declined by bank')
})
