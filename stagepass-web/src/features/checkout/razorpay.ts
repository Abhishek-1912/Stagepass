import type { CheckoutInfo, VerifyPaymentBody } from './types'

const SCRIPT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'

interface RazorpayHandlerResponse {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

interface RazorpayFailure {
  error?: { description?: string }
}

interface RazorpayInstance {
  open(): void
  on(event: 'payment.failed', cb: (response: RazorpayFailure) => void): void
}

export type RazorpayCtor = new (options: Record<string, unknown>) => RazorpayInstance

declare global {
  interface Window {
    Razorpay?: RazorpayCtor
  }
}

/** The buyer closed the Razorpay window without paying. */
export class CheckoutDismissed extends Error {
  /** Razorpay's description of the last failed attempt, if any. */
  readonly lastFailure: string | null

  constructor(lastFailure: string | null) {
    super(lastFailure ?? 'dismissed')
    this.name = 'CheckoutDismissed'
    this.lastFailure = lastFailure
  }
}

let loading: Promise<RazorpayCtor> | null = null

/** Loads Razorpay Checkout once per page; rejects if it can't load within {@code timeoutMs}. */
export function loadRazorpay(timeoutMs = 15_000): Promise<RazorpayCtor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay)
  if (loading) return loading
  loading = new Promise<RazorpayCtor>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_SRC
    script.async = true
    const fail = (message: string) => {
      clearTimeout(timer)
      script.remove()
      loading = null
      reject(new Error(message))
    }
    const timer = setTimeout(() => fail('Razorpay took too long to load. Check your connection and try again.'), timeoutMs)
    script.onload = () => {
      clearTimeout(timer)
      if (window.Razorpay) resolve(window.Razorpay)
      else fail('Razorpay could not start.')
    }
    script.onerror = () => fail('Could not load Razorpay. Check your connection and try again.')
    document.head.appendChild(script)
  })
  return loading
}

export interface Prefill {
  name?: string
  email?: string
  contact?: string
}

/**
 * Opens Razorpay Checkout for an order. Resolves with the ids the server needs to verify the payment.
 * Failed attempts keep the window open so the buyer can retry; closing it rejects with {@link CheckoutDismissed}.
 */
export async function openRazorpay(info: CheckoutInfo, prefill: Prefill, description: string): Promise<Omit<VerifyPaymentBody, 'bookingId'>> {
  const Razorpay = await loadRazorpay()
  return new Promise((resolve, reject) => {
    let lastFailure: string | null = null
    let settled = false
    const rzp = new Razorpay({
      key: info.razorpayKeyId,
      order_id: info.razorpayOrderId,
      amount: info.amountPaise,
      currency: info.currency,
      name: info.companyName,
      description,
      prefill,
      theme: { color: '#6C4CF1' },
      handler: (r: RazorpayHandlerResponse) => {
        if (settled) return
        settled = true
        resolve({ razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature })
      },
      modal: {
        ondismiss: () => {
          if (settled) return
          settled = true
          reject(new CheckoutDismissed(lastFailure))
        },
      },
    })
    rzp.on('payment.failed', (r) => {
      lastFailure = r.error?.description ?? 'Payment failed'
    })
    rzp.open()
  })
}

/** Test hook: forget the cached loader. */
export function resetRazorpayLoader() {
  loading = null
}
