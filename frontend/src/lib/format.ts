export function formatMoney(amount: number, currency: string = 'CAD') {
  let formatted: string
  try {
    formatted = new Intl.NumberFormat('en-CA', {
      style: 'currency',
      currency: currency || 'CAD',
      currencyDisplay: 'narrowSymbol',
      minimumFractionDigits: 2,
    }).format(amount)
  } catch {
    formatted = `${amount.toFixed(2)} ${currency}`
  }

  return currency === 'USD' ? `US${formatted}` : formatted
}

const dateFormat = new Intl.DateTimeFormat('en-CA', { month: 'short', day: 'numeric', year: 'numeric' })

/** "2026-11-19" -> "Nov 19, 2026" (dates are parsed as local, not UTC). */
export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  if (!y || !m || !d) return value
  return dateFormat.format(new Date(y, m - 1, d))
}

export function timeAgo(value: string | null | undefined, now = Date.now()) {
  if (!value) return ''
  const then = new Date(value).getTime()
  if (Number.isNaN(then)) return ''
  const minutes = Math.max(0, Math.round((now - then) / 60_000))
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min${minutes === 1 ? '' : 's'} ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} hr${hours === 1 ? '' : 's'} ago`
  const days = Math.round(hours / 24)
  if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`
  return formatDate(value)
}

export function initials(name: string) {
  const parts = name.replace(/[^\p{L}\p{N} ]/gu, ' ').split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}
