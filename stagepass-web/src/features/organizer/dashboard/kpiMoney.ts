/** Whole rupees for headline cards: `99800` → `₹998`, `94810` → `₹948.10`, `0` → `₹0`. */
export function kpiMoney(paise: number) {
  const rupees = paise / 100
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: paise % 100 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(rupees)
}
