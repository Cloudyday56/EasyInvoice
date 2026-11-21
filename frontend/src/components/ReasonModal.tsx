import { useEffect, useState } from 'react'
import { MessageSquareText, X } from 'lucide-react'
import { cn } from '../lib/cn'

interface ReasonModalProps {
  request: {
    title: string
    description: string
    confirmLabel: string
    tone: 'dark' | 'danger'
    optional?: boolean
  } | null
  busy: boolean
  onClose: () => void
  onConfirm: (reason: string) => void
}

/** Every human decision in the pipeline carries a written reason for the audit trail. */
export function ReasonModal({ request, busy, onClose, onConfirm }: ReasonModalProps) {
  const [reason, setReason] = useState('')

  useEffect(() => {
    if (request) setReason('')
  }, [request])

  if (!request) return null
  const canSubmit = !busy && (request.optional || reason.trim().length > 0)

  return (
    <div className="fixed inset-0 z-[65] flex items-end justify-center sm:items-center sm:p-6">
      <div className="absolute inset-0 bg-slate-900/30 backdrop-blur-[3px]" onClick={onClose} aria-hidden="true" />
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="reason-title"
        onSubmit={(event) => {
          event.preventDefault()
          if (canSubmit) onConfirm(reason.trim())
        }}
        className="animate-pop relative w-full max-w-[480px] rounded-t-3xl border border-white/60 bg-white p-5 shadow-2xl shadow-slate-900/20 sm:rounded-3xl sm:p-6"
      >
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-white',
              request.tone === 'danger' ? 'bg-rose-500' : 'bg-slate-900',
            )}
          >
            <MessageSquareText size={18} />
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="reason-title" className="text-[16px] font-extrabold tracking-[-0.02em] text-slate-900">
              {request.title}
            </h2>
            <p className="mt-0.5 text-[11.5px] leading-relaxed text-slate-500">{request.description}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={17} />
          </button>
        </div>

        <textarea
          autoFocus
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          placeholder={request.optional ? 'Comment (optional)' : 'Reason (required, kept in the audit trail)'}
          className="mt-4 w-full resize-none rounded-xl border border-slate-200 bg-white px-3 py-2 text-[12px] leading-relaxed text-slate-700 placeholder:text-slate-400 focus:border-sky-300 focus:outline-none focus:ring-2 focus:ring-sky-100"
        />

        <div className="mt-4 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="action-secondary">
            Cancel
          </button>
          <button
            type="submit"
            disabled={!canSubmit}
            className={cn('action-primary', request.tone === 'danger' && 'action-primary--danger')}
          >
            {busy ? 'Working…' : request.confirmLabel}
          </button>
        </div>
      </form>
    </div>
  )
}
