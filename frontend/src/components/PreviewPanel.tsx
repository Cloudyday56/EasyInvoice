import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  Ban,
  Check,
  CircleDashed,
  Clock,
  Loader2,
  Undo2,
  CreditCard,
  Download,
  FileText,
  History,
  Landmark,
  Mail,
  Paperclip,
  ShieldAlert,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Invoice } from '../types'
import type { NodeProgress, UserDto } from '../api/client'
import { pipelineNodes } from '../api/adapt'
import { formatMoney } from '../lib/format'
import { cn } from '../lib/cn'
import { InvoiceDocument } from './InvoiceDocument'
import { Avatar, RiskPill, StatusPill } from './ui'

export type PanelActionKind = 'approve' | 'release' | 'resolve' | 'reject' | 'return' | 'paid' | 'info'

export interface PanelAction {
  label: string
  tone: 'dark' | 'danger' | 'success'
  kind: PanelActionKind
  disabled?: boolean
  onClick?: () => void
}

interface PreviewPanelProps {
  invoice: Invoice | null
  users: UserDto[]
  primary: PanelAction | null
  secondary: PanelAction[]
  onBack: () => void
  onDownload: () => void
  onAudit: () => void
  onOpenCard: () => void
}

const approvalStatusLabels: Record<string, string> = {
  PENDING: 'Waiting',
  APPROVED: 'Approved',
  REJECTED: 'Declined',
  RETURNED: 'Returned',
  VOID: 'Cancelled',
}

const actionIcons: Record<PanelActionKind, LucideIcon> = {
  approve: Check,
  release: Check,
  resolve: ShieldAlert,
  reject: Ban,
  return: Undo2,
  paid: Check,
  info: Clock,
}

export function PreviewPanel({
  invoice,
  users,
  primary,
  secondary,
  onBack,
  onDownload,
  onAudit,
  onOpenCard,
}: PreviewPanelProps) {
  const [tab, setTab] = useState<'document' | 'email'>('document')

  useEffect(() => {
    setTab('document')
  }, [invoice?.id])

  const action = primary

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

  const cardReady = Boolean(invoice.card?.canRedeem)
  const shownAction = cardReady ? { ...action, disabled: false, onClick: onOpenCard } : action

  const ActionIcon = actionIcons[shownAction.kind]
  const paymentLocked = Boolean(invoice.payment.locked)
  const cardEntry = invoice.payment.kind === 'card' && !paymentLocked
  const PaymentIcon = paymentLocked
    ? ShieldAlert
    : invoice.payment.kind === 'card'
      ? CreditCard
      : Landmark
  const paymentLabel = paymentLocked
    ? 'Payment on hold'
    : invoice.payment.kind === 'card'
      ? 'CARD · VIRTUAL'
      : 'EFT · BANK'
  const paymentValue = `${invoice.payment.label} ${invoice.payment.detail}`.trim()
  const paymentFactClass = cn(
    'min-w-0 rounded-lg border px-3 py-2',
    paymentLocked
      ? 'border-rose-200 bg-rose-50'
      : invoice.payment.kind === 'card'
        ? 'border-sky-200 bg-sky-50'
        : 'border-slate-200 bg-slate-50',
  )
  const paymentLabelClass = cn(
    'flex items-center gap-1 text-[9.5px] font-extrabold uppercase tracking-[0.08em]',
    paymentLocked
      ? 'text-rose-700'
      : invoice.payment.kind === 'card'
        ? 'text-sky-700'
        : 'text-slate-500',
  )

  return (
    <section className="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#F7F7F5]">
      <header className="flex h-[64px] shrink-0 items-center justify-between border-b border-[#E8E8E4] bg-white px-3 sm:px-5">
        <div className="flex min-w-0 items-center gap-2.5">
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
            aria-label="View activity and audit trail"
            title="Activity & audit trail"
            className="icon-btn"
          >
            <History size={16} />
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
              <h3 className="text-[12px] font-bold text-slate-900">Risk overview</h3>
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

          <PipelineTracker nodes={pipelineNodes(invoice.raw, invoice.detail)} />

          {invoice.detail && invoice.detail.approvalSteps.length > 0 && (
            <div className="rounded-xl border border-slate-200 bg-white p-4">
              <h3 className="text-[12px] font-bold text-slate-900">Approvals</h3>
              <ol className="mt-2 space-y-1.5">
                {invoice.detail.approvalSteps.map((step) => {
                  const name = (id: string | null) => users.find((u) => u.id === id)?.fullName ?? id ?? 'unassigned'
                  return (
                    <li key={step.id} className="flex flex-wrap items-center gap-2 text-[11px] text-slate-600">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-100 text-[9.5px] font-bold text-slate-500">
                        {step.stepNo}
                      </span>
                      <span className="font-semibold text-slate-800">{name(step.assignedUserId)}</span>
                      {step.actingFor && <span className="text-slate-400">on behalf of {name(step.actingFor)}</span>}
                      {step.reason && <span className="text-slate-400">· {step.reason}</span>}
                      <span
                        className={cn(
                          'ml-auto rounded-full px-2 py-0.5 text-[9.5px] font-bold uppercase tracking-wide',
                          step.approved
                            ? 'bg-emerald-50 text-emerald-700'
                            : step.pending
                              ? 'bg-sky-50 text-sky-700'
                              : 'bg-rose-50 text-rose-700',
                        )}
                      >
                        {approvalStatusLabels[step.status] ?? step.status}
                      </span>
                    </li>
                  )
                })}
              </ol>
            </div>
          )}

          <div className="grid grid-cols-[repeat(auto-fit,minmax(132px,1fr))] gap-2">
            <Fact label="Amount" value={formatMoney(invoice.amount, invoice.currency)} />
            <Fact label={invoice.taxLabel} value={invoice.taxAmount > 0 ? formatMoney(invoice.taxAmount, invoice.currency) : 'Zero-rated'} />
            <Fact
              label="Due"
              value={invoice.dueDate}
              danger={invoice.flags.includes('overdue')}
            />
            <Fact label="PO number" value={invoice.poNumber ?? '—'} />
            {cardEntry ? (
              <button
                type="button"
                onClick={onOpenCard}
                aria-label="Open Brim virtual card"
                title="Open Brim virtual card"
                className={cn(paymentFactClass, 'text-left transition hover:border-sky-300 hover:bg-sky-100/70')}
              >
                <div className={paymentLabelClass}>
                  <PaymentIcon size={11} strokeWidth={2.2} />
                  Payment method
                  <span className="ml-auto text-[9px] font-bold normal-case tracking-normal text-sky-600">View card ↗</span>
                </div>
                <p
                  className="mt-0.5 truncate text-[11.5px] font-bold text-slate-800"
                  title={`${paymentLabel} · ${paymentValue}`}
                >
                  {paymentLabel}
                  <span className="ml-1 font-medium text-slate-500">{paymentValue}</span>
                </p>
              </button>
            ) : (
              <div className={paymentFactClass}>
                <div className={paymentLabelClass}>
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
            )}
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
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={shownAction.onClick}
              disabled={shownAction.disabled || !shownAction.onClick}
              title={shownAction.label}
              className={cn(
                'action-primary',
                shownAction.tone === 'danger' && 'action-primary--danger',
                shownAction.tone === 'success' && 'action-primary--success',
              )}
            >
              <ActionIcon size={15} strokeWidth={2.2} />
              <span className="truncate">{shownAction.label}</span>
            </button>

            <button type="button" onClick={onAudit} className="action-secondary">
              <History size={14} /> Audit trail
            </button>
            {secondary.map((item) => {
              const Icon = actionIcons[item.kind]
              return (
                <button
                  key={item.kind}
                  type="button"
                  onClick={item.onClick}
                  disabled={item.disabled}
                  className={cn('action-secondary', item.tone === 'danger' && 'text-rose-600')}
                >
                  <Icon size={14} /> {item.label}
                </button>
              )
            })}
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

const nodeStateStyles: Record<NodeProgress['state'], { dot: string; text: string }> = {
  pending: { dot: 'bg-slate-200 text-slate-400', text: 'text-slate-400' },
  running: { dot: 'bg-sky-500 text-white', text: 'text-sky-700' },
  done: { dot: 'bg-emerald-500 text-white', text: 'text-slate-700' },
  flagged: { dot: 'bg-amber-400 text-white', text: 'text-amber-700' },
  blocked: { dot: 'bg-rose-500 text-white', text: 'text-rose-700' },
  waiting: { dot: 'bg-sky-100 text-sky-600 ring-1 ring-sky-300', text: 'text-sky-700' },
  retrying: { dot: 'bg-orange-400 text-white', text: 'text-orange-700' },
  ignored: { dot: 'bg-slate-300 text-white', text: 'text-slate-500' },
}

function PipelineTracker({ nodes }: { nodes: NodeProgress[] }) {
  const done = nodes.filter((n) => n.state === 'done' || n.state === 'flagged').length
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-[12px] font-bold text-slate-900">Progress</h3>
        <span className="text-[10.5px] font-semibold text-slate-400">
          {done}/{nodes.length} steps
        </span>
      </div>
      <ol className="mt-3 grid grid-cols-1 gap-x-4 gap-y-1.5 sm:grid-cols-2">
        {nodes.map((node, index) => {
          const style = nodeStateStyles[node.state] ?? nodeStateStyles.pending
          const Icon =
            node.state === 'done'
              ? Check
              : node.state === 'running' || node.state === 'retrying'
                ? Loader2
                : node.state === 'blocked'
                  ? ShieldAlert
                  : node.state === 'waiting'
                    ? Clock
                    : node.state === 'pending'
                      ? null
                      : CircleDashed
          return (
            <li key={node.node} className="flex min-w-0 items-start gap-2" title={node.message ?? undefined}>
              <span
                className={cn(
                  'mt-px flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full text-[9px] font-bold',
                  style.dot,
                )}
              >
                {Icon ? (
                  <Icon size={10} strokeWidth={3} className={node.state === 'running' ? 'animate-spin' : undefined} />
                ) : (
                  index + 1
                )}
              </span>
              <div className="min-w-0">
                <p className={cn('truncate text-[11px] font-semibold', style.text)}>{node.label}</p>
                {node.message && <p className="truncate text-[10px] text-slate-400">{node.message}</p>}
              </div>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
