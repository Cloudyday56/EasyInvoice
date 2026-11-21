import { useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  CreditCard,
  Download,
  FileText,
  Flag,
  Landmark,
  Mail,
  MoreHorizontal,
  Paperclip,
  Reply,
  Send,
  ShieldAlert,
  UserPlus,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Invoice } from '../types'
import { formatMoney } from '../lib/format'
import { cn } from '../lib/cn'
import { InvoiceDocument } from './InvoiceDocument'
import { Avatar, RiskPill, StatusPill } from './ui'

interface PreviewPanelProps {
  invoice: Invoice | null
  onBack: () => void
  onPrimaryAction: () => void
  onAssign: (team: string) => void
  onFlag: () => void
  onReply: (message: string) => void
  onDownload: () => void
  onAudit: () => void
}

const teams = ['AP Team', 'Treasury', 'Procurement', 'Risk & Fraud']

type ActionTone = 'dark' | 'danger' | 'success'

interface ActionConfig {
  label: string
  icon: LucideIcon
  tone: ActionTone
}

function primaryAction(invoice: Invoice): ActionConfig {
  const amount = formatMoney(invoice.amount, invoice.currency)

  if (invoice.status === 'Paid') {
    return { label: 'Paid · View receipt', icon: Check, tone: 'success' }
  }
  if (invoice.flags.includes('fraud')) {
    return { label: 'Hold & Verify Vendor', icon: ShieldAlert, tone: 'danger' }
  }
  if (invoice.status === 'On hold' || invoice.flags.includes('critical')) {
    return { label: 'Resolve Exception', icon: ShieldAlert, tone: 'danger' }
  }
  if (invoice.status === 'Approved' || invoice.status === 'Scheduled') {
    return { label: `Release Payment (${amount})`, icon: Check, tone: 'success' }
  }
  return {
    label: `Approve & Pay with ${invoice.payment.kind === 'card' ? 'Card' : 'EFT'} (${amount})`,
    icon: Check,
    tone: 'dark',
  }
}

export function PreviewPanel({
  invoice,
  onBack,
  onPrimaryAction,
  onAssign,
  onFlag,
  onReply,
  onDownload,
  onAudit,
}: PreviewPanelProps) {
  const [tab, setTab] = useState<'document' | 'email'>('document')
  const [showAssign, setShowAssign] = useState(false)
  const [replyOpen, setReplyOpen] = useState(false)
  const [replyText, setReplyText] = useState('')

  useEffect(() => {
    setTab('document')
    setShowAssign(false)
    setReplyOpen(false)
    setReplyText('')
  }, [invoice?.id])

  const action = useMemo(() => (invoice ? primaryAction(invoice) : null), [invoice])

  if (!invoice || !action) {
    return (
      <section className="flex min-h-0 flex-1 flex-col items-center justify-center bg-[#F7F7F5] px-6 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-slate-300 shadow-sm ring-1 ring-slate-200">
          <Mail size={24} strokeWidth={1.7} />
        </div>
        <p className="mt-4 text-[14px] font-bold text-slate-800">Nothing selected</p>
        <p className="mt-1 max-w-[260px] text-[12px] leading-relaxed text-slate-400">
          Choose an invoice from the inbox to review its document and payment details.
        </p>
        <button type="button" onClick={onBack} className="action-secondary mt-4 lg:hidden">
          <ArrowLeft size={14} /> Back to inbox
        </button>
      </section>
    )
  }

  const ActionIcon = action.icon
  const paymentLocked = Boolean(invoice.payment.locked)
  const PaymentIcon = paymentLocked
    ? ShieldAlert
    : invoice.payment.kind === 'card'
      ? CreditCard
      : Landmark
  const paymentLabel = paymentLocked
    ? 'EFT · LOCKED'
    : invoice.payment.kind === 'card'
      ? 'CARD · VIRTUAL'
      : 'EFT · BANK'
  const paymentValue = `${invoice.payment.label} ${invoice.payment.detail}`.trim()

  const sendReply = () => {
    const message = replyText.trim()
    if (!message) return
    onReply(message)
    setReplyText('')
    setReplyOpen(false)
  }

  return (
    <section className="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#F7F7F5]">
      <header className="flex h-[64px] shrink-0 items-center justify-between border-b border-[#E8E8E4] bg-white px-3 sm:px-5">
        <div className="flex min-w-0 items-center gap-2.5">
          <button
            type="button"
            onClick={onBack}
            aria-label="Back to inbox"
            className="icon-btn lg:hidden"
          >
            <ArrowLeft size={17} />
          </button>
          <div className="hidden h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-sky-50 text-sky-600 sm:flex">
            <FileText size={16} />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h2 className="truncate text-[13px] font-bold text-slate-900">{invoice.supplier}</h2>
              <span className="hidden sm:inline-flex">
                <StatusPill status={invoice.status} />
              </span>
            </div>
            <p className="truncate text-[11px] text-slate-400">{invoice.subject}</p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-0.5">
          <button
            type="button"
            onClick={() => setReplyOpen(true)}
            aria-label="Reply to supplier"
            title="Reply to supplier"
            className="icon-btn"
          >
            <Send size={16} />
          </button>
          <button
            type="button"
            onClick={onDownload}
            aria-label="Download PDF"
            title="Download PDF"
            className="icon-btn"
          >
            <Download size={16} />
          </button>
          <button
            type="button"
            onClick={onAudit}
            aria-label="View audit trail"
            title="View audit trail"
            className="icon-btn"
          >
            <MoreHorizontal size={16} />
          </button>
        </div>
      </header>

      <div className="scrollbar-thin min-h-0 flex-1 overflow-y-auto px-3 py-4 sm:px-6">
        <div className="mx-auto max-w-[860px] space-y-4">
          <div className="rounded-xl border border-sky-100 bg-gradient-to-br from-sky-50/80 to-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.03)]">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="flex h-5 w-5 items-center justify-center rounded-md bg-sky-600 text-[10px] font-bold text-white">
                ✦
              </span>
              <h3 className="text-[12px] font-bold text-slate-900">AI Fraud Summary</h3>
              <span className="ml-auto flex flex-wrap items-center gap-1.5">
                <RiskPill
                  tone={invoice.risk.tone}
                  label={invoice.risk.label}
                  score={invoice.risk.score}
                />
              </span>
            </div>
            <p className="text-[12px] leading-[1.6] text-slate-600">{invoice.summary}</p>
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {invoice.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-md border border-slate-200 bg-white px-2 py-0.5 text-[10px] font-medium text-slate-500"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-[repeat(auto-fit,minmax(132px,1fr))] gap-2">
            <Fact label="Amount" value={formatMoney(invoice.amount, invoice.currency)} />
            <Fact label={invoice.taxLabel} value={invoice.taxAmount > 0 ? formatMoney(invoice.taxAmount, invoice.currency) : 'Zero-rated'} />
            <Fact
              label="Due"
              value={invoice.dueDate}
              danger={invoice.flags.includes('overdue')}
            />
            <Fact label="PO number" value={invoice.poNumber ?? '—'} />
            <div
              className={cn(
                'min-w-0 rounded-lg border px-3 py-2',
                paymentLocked
                  ? 'border-rose-200 bg-rose-50'
                  : invoice.payment.kind === 'card'
                    ? 'border-sky-200 bg-sky-50'
                    : 'border-slate-200 bg-slate-50',
              )}
            >
              <div
                className={cn(
                  'flex items-center gap-1 text-[9.5px] font-extrabold uppercase tracking-[0.08em]',
                  paymentLocked
                    ? 'text-rose-700'
                    : invoice.payment.kind === 'card'
                      ? 'text-sky-700'
                      : 'text-slate-500',
                )}
              >
                <PaymentIcon size={11} strokeWidth={2.2} />
                Payment method
              </div>
              <p
                className="mt-0.5 truncate text-[11.5px] font-bold text-slate-800"
                title={`${paymentLabel} · ${paymentValue}`}
              >
                {paymentLabel}
                <span className="ml-1 font-medium text-slate-500">{paymentValue}</span>
              </p>
            </div>
          </div>

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.03)]">
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/70 px-2 sm:px-3">
              <div className="flex items-center">
                <TabButton
                  active={tab === 'document'}
                  onClick={() => setTab('document')}
                  icon={FileText}
                  label="Invoice document"
                />
                <TabButton
                  active={tab === 'email'}
                  onClick={() => setTab('email')}
                  icon={Mail}
                  label="Original email"
                />
              </div>
              <span className="hidden truncate pl-2 text-[10px] font-medium text-slate-400 sm:block">
                {invoice.attachment.name}
              </span>
            </div>

            {tab === 'document' ? (
              <div className="bg-[#EEF1F5] p-3 sm:p-5">
                <InvoiceDocument invoice={invoice} />
              </div>
            ) : (
              <div className="p-4 sm:p-6">
                <div className="flex items-center gap-3">
                  <Avatar
                    initials={invoice.supplierInitials}
                    className={invoice.avatarClass}
                    size="md"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[12px] font-bold text-slate-800">
                      {invoice.supplier}
                    </p>
                    <p className="truncate text-[11px] text-slate-400">{invoice.supplierEmail}</p>
                  </div>
                  <span className="shrink-0 text-[10.5px] font-medium text-slate-400">
                    {invoice.received}
                  </span>
                </div>
                <div className="mt-4 rounded-lg border border-slate-200 bg-white px-4 py-3">
                  <p className="whitespace-pre-line text-[12px] leading-[1.7] text-slate-700">
                    {invoice.body}
                  </p>
                </div>
                <div className="mt-3 flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-white text-slate-400 ring-1 ring-slate-200">
                    <Paperclip size={15} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[11px] font-semibold text-slate-700">
                      {invoice.attachment.name}
                    </p>
                    <p className="text-[10px] text-slate-400">
                      {invoice.attachment.size} · {invoice.attachment.pages} page
                      {invoice.attachment.pages > 1 ? 's' : ''}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={onDownload}
                    aria-label="Download attachment"
                    className="icon-btn"
                  >
                    <Download size={15} />
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="h-2" />
        </div>
      </div>

      <footer data-preview-footer className="shrink-0 border-t border-slate-200 bg-white px-3 py-3 sm:px-6">
        <div className="mx-auto max-w-[860px]">
          {replyOpen && (
            <div className="mb-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
              <div className="mb-2 flex items-center justify-between">
                <p className="text-[11px] font-bold text-slate-700">
                  Reply to {invoice.supplier}
                </p>
                <button
                  type="button"
                  onClick={() => setReplyOpen(false)}
                  aria-label="Close reply"
                  className="icon-btn"
                >
                  <X size={14} />
                </button>
              </div>
              <textarea
                value={replyText}
                onChange={(event) => setReplyText(event.target.value)}
                rows={3}
                placeholder="Ask the supplier to confirm the bank change or correct the invoice…"
                className="w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-[12px] leading-relaxed text-slate-700 placeholder:text-slate-400 focus:border-sky-300 focus:outline-none focus:ring-2 focus:ring-sky-100"
              />
              <div className="mt-2 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setReplyOpen(false)}
                  className="action-secondary"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={sendReply}
                  disabled={!replyText.trim()}
                  className="action-primary"
                >
                  <Send size={14} /> Send reply
                </button>
              </div>
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={onPrimaryAction}
              className={cn(
                'action-primary',
                action.tone === 'danger' && 'action-primary--danger',
                action.tone === 'success' && 'action-primary--success',
              )}
            >
              <ActionIcon size={15} strokeWidth={2.2} />
              <span className="truncate">{action.label}</span>
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setShowAssign((open) => !open)}
                aria-expanded={showAssign}
                className="action-secondary"
              >
                <UserPlus size={14} /> Assign <ChevronDown size={12} />
              </button>
              {showAssign && (
                <div className="absolute bottom-[calc(100%+6px)] left-0 z-20 w-44 overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-[0_12px_32px_-12px_rgba(15,23,42,0.25)]">
                  {teams.map((team) => (
                    <button
                      key={team}
                      type="button"
                      onClick={() => {
                        onAssign(team)
                        setShowAssign(false)
                      }}
                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-[12px] font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-sky-50 text-[9px] font-bold text-sky-600">
                        {team
                          .split(' ')
                          .map((part) => part[0])
                          .join('')
                          .slice(0, 2)}
                      </span>
                      {team}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => setReplyOpen(true)}
              className="action-secondary"
            >
              <Reply size={14} /> Reply
            </button>
            <button type="button" onClick={onFlag} className="action-secondary">
              <Flag size={14} /> Hold
            </button>
            <span className="ml-auto hidden items-center gap-1.5 text-[10.5px] font-medium text-slate-400 xl:flex">
              Received {invoice.received}
            </span>
          </div>
        </div>
      </footer>
    </section>
  )
}

function Fact({
  label,
  value,
  danger,
}: {
  label: string
  value: string
  danger?: boolean
}) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2">
      <div className="truncate text-[9.5px] font-extrabold uppercase tracking-[0.08em] text-slate-400">
        {label}
      </div>
      <p
        className={cn(
          'mt-0.5 truncate text-[12px] font-bold',
          danger ? 'text-rose-600' : 'text-slate-800',
        )}
        title={value}
      >
        {value}
      </p>
    </div>
  )
}

function TabButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean
  onClick: () => void
  icon: LucideIcon
  label: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'relative flex items-center gap-1.5 px-3 py-2.5 text-[11px] font-semibold transition',
        active ? 'text-sky-700' : 'text-slate-400 hover:text-slate-700',
      )}
    >
      <Icon size={13} /> {label}
      {active && (
        <span className="absolute inset-x-2 bottom-0 h-[2px] rounded-full bg-sky-600" />
      )}
    </button>
  )
}
