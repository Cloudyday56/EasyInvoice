import { CreditCard, X } from 'lucide-react'
import type { CardInfo, CardScenario } from '../api/client'
import { CardRedemptionPanel } from './CardRedemptionPanel'

interface VirtualCardModalProps {
  open: boolean
  card: CardInfo | null
  subtitle?: string
  busy: boolean
  onRedeem: (scenario: CardScenario) => void
  onClose: () => void
}

/** Focused, blurred-background view of the issued Brim virtual card and its test controls. */
export function VirtualCardModal({ open, card, subtitle, busy, onRedeem, onClose }: VirtualCardModalProps) {
  if (!open || !card) return null

  return (
    <div className="fixed inset-0 z-[74] flex items-center justify-center p-4" role="presentation">
      <div
        className="absolute inset-0 bg-slate-950/55 backdrop-blur-md"
        onClick={onClose}
        aria-hidden="true"
      />
      <section
        role="dialog"
        aria-modal="true"
        aria-label="Brim virtual card"
        className="animate-pop relative flex max-h-[calc(100vh-2rem)] w-full max-w-[480px] flex-col overflow-hidden rounded-3xl border border-white/70 bg-white shadow-2xl"
      >
        <header className="flex shrink-0 items-start gap-3 border-b border-slate-200 bg-white px-5 py-4">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-sky-100 text-sky-700">
            <CreditCard size={18} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-[15px] font-extrabold tracking-[-0.01em] text-slate-900">
              Brim virtual card
            </h2>
            <p className="mt-0.5 text-[11px] text-slate-500">
              {subtitle ?? 'Single-use card issued for this payment'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close virtual card"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={17} />
          </button>
        </header>

        <div className="scrollbar-thin min-h-0 flex-1 overflow-y-auto bg-[#F7F7F5] p-4 sm:p-5">
          <CardRedemptionPanel card={card} busy={busy} onRedeem={onRedeem} featured />
        </div>
      </section>
    </div>
  )
}
