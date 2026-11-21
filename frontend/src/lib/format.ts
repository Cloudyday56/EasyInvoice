export function formatMoney(amount: number, currency: 'CAD' | 'USD' = 'CAD') {
  const formatted = new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency,
    currencyDisplay: 'narrowSymbol',
    minimumFractionDigits: 2,
  }).format(amount)

  return currency === 'USD' ? `US${formatted}` : formatted
}
