/** Parses a rupee amount typed by a person (`'499'`, `'1,499.50'`) into integer paise. Throws on invalid input. */
export function rupeesToPaise(input: string): number {
  const cleaned = input.trim().replace(/,/g, '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) {
    throw new Error('Enter an amount in rupees with up to 2 decimals')
  }
  const [whole, fraction = ''] = cleaned.split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const rupeesWithPaise = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** `49900` → `₹499`, `49950` → `₹499.50`, `0` → `Free`. */
export function formatPaise(paise: number): string {
  if (paise === 0) return 'Free'
  return paise % 100 === 0 ? rupees.format(paise / 100) : rupeesWithPaise.format(paise / 100)
}

/** Paise as a plain rupee string for form inputs: `49950` → `'499.50'`, `49900` → `'499'`. */
export function paiseToRupeesInput(paise: number): string {
  return paise % 100 === 0 ? String(paise / 100) : (paise / 100).toFixed(2)
}

/** Always to the paisa, as the server computed it: `105104` → `₹1,051.04`, `0` → `₹0`. */
export function formatPaiseExact(paise: number): string {
  return paise === 0 ? '₹0' : rupeesWithPaise.format(paise / 100)
}
