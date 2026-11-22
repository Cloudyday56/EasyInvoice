import { Ban, Check, Clock, CreditCard, Landmark, Loader2, ShieldCheck } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { CardInfo, CardScenario } from '../api/client'
import { formatMoney } from '../lib/format'
import { cn } from '../lib/cn'

interface CardRedemptionPanelProps {
  card: CardInfo
  busy: boolean
  onRedeem: (scenario: CardScenario) => void
  /** Larger card artwork for the overlay view. */
  featured?: boolean
}

interface StatusMeta {
  label: string
  note: string
  tone: 'issued' | 'used' | 'declined'
  icon: LucideIcon
}

function statusOf(card: CardInfo): StatusMeta {
  if (card.status === 'USED') {
    return {
      label: 'Used',
      tone: 'used',
      icon: Check,
      note: `Card redeemed · transaction ${card.transactionId ?? 'recorded'}. Payment reference ${card.redemptionReference ?? 'pending'}.`,
    }
  }
  if (card.status === 'DECLINED' || card.status === 'EXPIRED') {
    return {
      label: 'Declined · EFT fallback',
      tone: 'declined',
      icon: Landmark,
      note: `Card declined. ${card.fallbackMethod ?? 'EFT'} fallback sent with reference ${card.redemptionReference ?? 'pending'}.`,
    }
  }
  return {
    label: 'Issued · waiting for redemption',
    tone: 'issued',
    icon: Clock,
    note: 'Locked to this vendor and exact amount. One successful charge sends the payment; a failed one falls back to EFT.',
  }
}

function shortDate(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('en-CA', { month: 'short', day: 'numeric' })
}

export function CardRedemptionPanel({ card, busy, onRedeem, featured = false }: CardRedemptionPanelProps) {
  const status = statusOf(card)
  const StatusIcon = status.icon
  const currency = card.currency ?? 'CAD'
  const interactive = card.canRedeem && !busy

  return (
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.03)]">
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 bg-slate-50/70 px-3 py-2.5 sm:px-4">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-sky-100 text-sky-700">
          <CreditCard size={14} />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-[12px] font-bold text-slate-900">Brim card redemption</h3>
          <p className="text-[10.5px] text-slate-500">Single-use card issued for this payment</p>
        </div>
        <span
          className={cn(
            'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide',
            status.tone === 'used' && 'border-emerald-100 bg-emerald-50 text-emerald-700',
            status.tone === 'declined' && 'border-amber-100 bg-amber-50 text-amber-700',
            status.tone === 'issued' && 'border-sky-100 bg-sky-50 text-sky-700',
          )}
        >
          <StatusIcon size={11} strokeWidth={2.4} /> {status.label}
        </span>
      </div>

      <div className="space-y-3 p-3 sm:p-4">
        <div
          className={cn(
            'relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br from-slate-900 via-slate-800 to-sky-900 p-4 text-white shadow-xl',
            featured && 'min-h-[220px] p-5 sm:p-6',
          )}
        >
          <div className="absolute -right-12 -top-16 h-40 w-40 rounded-full bg-sky-400/20" />
          <div className="absolute -bottom-16 left-10 h-32 w-32 rounded-full bg-indigo-400/10" />
          <div className="absolute right-6 top-1/2 h-24 w-24 -translate-y-1/2 rounded-full border border-white/10" />
          <div className="relative flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-[9px] font-extrabold uppercase tracking-[0.18em] text-sky-200">Brim virtual card</p>
              <p
                className={cn(
                  'mt-1 truncate font-extrabold tracking-[0.16em]',
                  featured ? 'text-[19px]' : 'text-[15px]',
                )}
              >
                {card.maskedNumber}
              </p>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-2">
              <span className="rounded-md border border-white/25 bg-white/10 px-1.5 py-0.5 text-[9px] font-black tracking-[0.2em] text-white">
                BRIM
              </span>
              <CreditCard size={20} className="text-sky-200" strokeWidth={1.7} />
            </div>
          </div>
          <div className="relative mt-4 flex items-center gap-2.5" aria-hidden="true">
            <span className="relative h-7 w-9 overflow-hidden rounded-md bg-gradient-to-br from-amber-200 via-yellow-400 to-amber-600 shadow-inner">
              <span className="absolute left-1/2 top-0 h-full w-px -translate-x-1/2 bg-amber-700/40" />
              <span className="absolute left-0 top-1/2 h-px w-full -translate-y-1/2 bg-amber-700/40" />
            </span>
            <span className="h-4 w-10 rounded-full border border-white/35" />
          </div>
          <dl
            className={cn(
              'relative grid grid-cols-2 gap-3 text-[10px] sm:grid-cols-3',
              featured ? 'mt-5' : 'mt-4',
            )}
          >
            <div className="min-w-0">
              <dt className="font-bold uppercase tracking-wide text-slate-400">Vendor lock</dt>
              <dd className="mt-0.5 truncate font-semibold text-white" title={card.merchantName ?? card.merchantId ?? ''}>
                {card.merchantName ?? card.merchantId ?? '—'}
              </dd>
            </div>
            <div className="min-w-0">
              <dt className="font-bold uppercase tracking-wide text-slate-400">Amount limit</dt>
              <dd className="mt-0.5 truncate font-semibold text-white">{formatMoney(card.amountLimit, currency)}</dd>
            </div>
            <div className="min-w-0">
              <dt className="font-bold uppercase tracking-wide text-slate-400">Expires · single use</dt>
              <dd className="mt-0.5 truncate font-semibold text-white">
                {shortDate(card.expiresAt)} · {card.singleUse ? 'Yes' : 'No'}
              </dd>
            </div>
          </dl>
        </div>

        <p className="text-[11px] leading-relaxed text-slate-600">{status.note}</p>

        {card.canRedeem && (
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <button
              type="button"
              disabled={!interactive}
              onClick={() => onRedeem('CORRECT')}
              className={cn('action-primary sm:col-span-2', busy && 'opacity-60')}
            >
              {busy ? <Loader2 size={14} className="animate-spin" /> : <ShieldCheck size={14} />}
              {busy ? 'Checking card…' : `Charge correct amount (${formatMoney(card.amountLimit, currency)})`}
            </button>
            <button type="button" disabled={!interactive} onClick={() => onRedeem('WRONG_MERCHANT')} className="action-secondary text-rose-600">
              <Ban size={13} /> Wrong vendor
            </button>
            <button type="button" disabled={!interactive} onClick={() => onRedeem('WRONG_AMOUNT')} className="action-secondary text-rose-600">
              <Landmark size={13} /> Wrong amount
            </button>
            <button type="button" disabled={!interactive} onClick={() => onRedeem('EXPIRED')} className="action-secondary text-rose-600 sm:col-span-2">
              <Clock size={13} /> Expired card
            </button>
          </div>
        )}

        {!card.canRedeem && card.status === 'ISSUED' && (
          <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-[10.5px] text-slate-500">
            Admins can simulate redemption from this panel.
          </p>
        )}

        <p className="text-[10px] leading-relaxed text-slate-400">
          This is a demo card network. Each attempt is validated against the vendor lock, amount limit and expiry,
          then written to the audit trail — including the EFT fallback.
        </p>
      </div>
    </section>
  )
}
