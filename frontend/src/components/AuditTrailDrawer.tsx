import { useRef, useState } from 'react'
import {
  ArrowDown,
  Ban,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock,
  Database,
  Download,
  FileText,
  History,
  Inbox,
  Landmark,
  Link2,
  Lock,
  MailPlus,
  PauseCircle,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  Undo2,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { AuditEventDto, UserDto, VerifyResult } from '../api/client'
import { auditHoldLabel, auditNodeLabel, auditPretty, auditStatusLabel } from '../api/adapt'
import { timeAgo } from '../lib/format'
import { cn } from '../lib/cn'

type Tone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

interface ActionMeta {
  label: string
  tone: Tone
  icon: LucideIcon
}

const ACTION_META: Record<string, ActionMeta> = {
  'invoice.received': { label: 'Invoice received', tone: 'info', icon: Inbox },
  'inbox.polled': { label: 'Inbox checked', tone: 'neutral', icon: MailPlus },
  'node.PASS': { label: 'Automated check passed', tone: 'success', icon: CheckCircle2 },
  'node.FLAG': { label: 'Review flag added', tone: 'warning', icon: ShieldAlert },
  'node.BLOCK': { label: 'Paused for review', tone: 'danger', icon: PauseCircle },
  'node.WAIT': { label: 'Waiting', tone: 'neutral', icon: Clock },
  'node.RETRY': { label: 'Retry scheduled', tone: 'warning', icon: RotateCcw },
  'node.IGNORE': { label: 'Marked as not an invoice', tone: 'neutral', icon: Ban },
  'status.changed': { label: 'Status updated', tone: 'info', icon: RefreshCw },
  'invoice.held': { label: 'Placed on hold', tone: 'warning', icon: PauseCircle },
  'hold.resolved': { label: 'Hold resolved', tone: 'success', icon: ShieldCheck },
  'approval.approved': { label: 'Approved', tone: 'success', icon: Check },
  'approval.rejected': { label: 'Rejected', tone: 'danger', icon: Ban },
  'approval.returned': { label: 'Returned to AP', tone: 'warning', icon: Undo2 },
  'payment.released': { label: 'Payment released', tone: 'success', icon: Landmark },
  'authz.denied': { label: 'Access denied', tone: 'danger', icon: Lock },
  'seed.loaded': { label: 'Sample data loaded', tone: 'neutral', icon: Database },
  'demo.reset': { label: 'Demo data reset', tone: 'neutral', icon: RotateCcw },
  'invoices.cleared': { label: 'Invoices cleared', tone: 'neutral', icon: RotateCcw },
}

/** Routine automation stays in the record, but does not clutter the default story. */
const ROUTINE_ACTIONS = new Set([
  'node.PASS',
  'node.WAIT',
  'node.RETRY',
  'status.changed',
  'inbox.polled',
  'seed.loaded',
])

const TONE_STYLES: Record<Tone, { dot: string; chip: string }> = {
  success: { dot: 'bg-emerald-500', chip: 'border-emerald-100 bg-emerald-50 text-emerald-700' },
  info: { dot: 'bg-sky-500', chip: 'border-sky-100 bg-sky-50 text-sky-700' },
  warning: { dot: 'bg-amber-500', chip: 'border-amber-100 bg-amber-50 text-amber-700' },
  danger: { dot: 'bg-rose-500', chip: 'border-rose-100 bg-rose-50 text-rose-700' },
  neutral: { dot: 'bg-slate-300', chip: 'border-slate-200 bg-slate-50 text-slate-600' },
}

const PAYLOAD_LABELS: Record<string, string> = {
  message: 'Details',
  reason: 'Reason',
  reasonCode: 'Code',
  riskScore: 'Risk score',
  flags: 'Signals',
  fraudSignals: 'Fraud signals',
  fraudScoreBreakdown: 'Score breakdown',
  scoreThresholds: 'Thresholds',
  overriddenSignals: 'Reviewed signals',
  queue: 'Queue',
  resumeNode: 'Resume at',
  amount: 'Amount',
  currency: 'Currency',
  method: 'Method',
  channel: 'Channel',
  file: 'File',
  sha256: 'File fingerprint',
  fileFingerprint: 'File fingerprint',
  by: 'By',
  stepNo: 'Step',
  action: 'Action',
  counts: 'Counts',
  error: 'Error',
  retryInMs: 'Retry in',
  attempts: 'Attempts',
}

function metaFor(event: AuditEventDto): ActionMeta {
  if (ACTION_META[event.action]) return ACTION_META[event.action]
  if (event.action?.startsWith('node.')) return ACTION_META['node.PASS']
  return { label: auditPretty(event.action ?? 'Activity'), tone: 'neutral', icon: History }
}

function actorName(event: AuditEventDto, users: UserDto[]) {
  if (!event.actorId || event.actorId === 'system') return 'Automation'
  return users.find((user) => user.id === event.actorId)?.fullName ?? event.actorId
}

function formatValue(key: string, value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (Array.isArray(value)) {
    const signalKeys = ['flags', 'fraudSignals', 'overriddenSignals']
    return value
      .map((entry) => {
        if (typeof entry !== 'string') return JSON.stringify(entry)
        return signalKeys.includes(key) ? auditPretty(entry) : entry
      })
      .join(', ')
  }
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([nestedKey, nested]) => `${auditPretty(nestedKey)} ${formatValue(nestedKey, nested)}`)
      .filter(Boolean)
      .join(' · ')
  }
  if (key === 'reasonCode') return auditPretty(String(value))
  if (key === 'sha256' || key === 'fileFingerprint') {
    const text = String(value)
    return text.length > 16 ? `${text.slice(0, 16)}…` : text
  }
  if (key === 'riskScore') return `${value}/100`
  if (key === 'queue') return auditHoldLabel(String(value))
  if (key === 'resumeNode') return auditNodeLabel(String(value))
  if (key === 'retryInMs' && typeof value === 'number') return `${Math.round(value / 1000)} seconds`
  if (key === 'amount' && typeof value === 'number') {
    return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
  }
  return String(value)
}

function payloadRows(event: AuditEventDto): Array<{ label: string; value: string }> {
  if (!event.payloadJson) return []
  try {
    const payload = JSON.parse(event.payloadJson) as Record<string, unknown>
    return Object.entries(payload)
      .filter(([key]) => key !== 'message')
      .map(([key, value]) => ({ label: PAYLOAD_LABELS[key] ?? auditPretty(key), value: formatValue(key, value) }))
      .filter((row) => row.value.length > 0)
  } catch {
    return []
  }
}

function payloadText(event: AuditEventDto): string | null {
  if (!event.payloadJson) return null
  try {
    return JSON.stringify(JSON.parse(event.payloadJson), null, 2)
  } catch {
    return event.payloadJson
  }
}

function fullTimestamp(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('en-CA', { dateStyle: 'medium', timeStyle: 'short' })
}

interface AuditTrailDrawerProps {
  open: boolean
  events: AuditEventDto[]
  users: UserDto[]
  title?: string
  subtitle?: string
  verification?: VerifyResult | null
  loading?: boolean
  onClose: () => void
  onExport?: () => void
}

/** Human-readable activity view over the same hash-chained events used by Verify chain. */
export function AuditTrailDrawer({
  open,
  events,
  users,
  title = 'Activity & audit trail',
  subtitle,
  verification = null,
  loading = false,
  onClose,
  onExport,
}: AuditTrailDrawerProps) {
  const listRef = useRef<HTMLDivElement>(null)
  const [showAll, setShowAll] = useState(false)

  if (!open) return null

  // App.tsx supplies events oldest-first for both invoice and global views.
  const ordered = events
  const visible = showAll ? ordered : ordered.filter((event) => !ROUTINE_ACTIONS.has(event.action))

  return (
    <div className="fixed inset-0 z-[72] flex justify-end" role="presentation">
      <div className="absolute inset-0 bg-slate-900/35 backdrop-blur-[2px]" onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="animate-pop relative flex h-full w-full max-w-[560px] flex-col border-l border-slate-200 bg-[#FAFAF8] shadow-2xl"
      >
        <header className="shrink-0 border-b border-slate-200 bg-white px-5 py-4 sm:px-6">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-900 text-white">
              <History size={18} />
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-[16px] font-extrabold tracking-[-0.02em] text-slate-900">{title}</h2>
              <p className="mt-0.5 truncate text-[11.5px] text-slate-500">
                {subtitle ?? 'A complete record of what happened, and who did it.'}
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close activity"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            >
              <X size={17} />
            </button>
          </div>

          {verification && (
            <div
              className={cn(
                'mt-4 flex items-start gap-2.5 rounded-xl border px-3 py-2.5',
                verification.valid
                  ? 'border-emerald-100 bg-emerald-50 text-emerald-800'
                  : 'border-rose-100 bg-rose-50 text-rose-800',
              )}
            >
              {verification.valid ? <ShieldCheck size={16} className="mt-0.5 shrink-0" /> : <ShieldAlert size={16} className="mt-0.5 shrink-0" />}
              <div className="min-w-0">
                <p className="text-[12px] font-bold">
                  {verification.valid ? 'Integrity check passed' : 'Integrity issue detected'}
                </p>
                <p className="mt-0.5 text-[11px] leading-relaxed opacity-80">
                  {verification.valid
                    ? `All ${verification.checked} events form an unbroken chain.`
                    : `Event #${verification.brokenAtId ?? '?'} — ${verification.problem ?? 'hash mismatch'}`}
                </p>
              </div>
            </div>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span
              title="Read from top to bottom · oldest → newest"
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10.5px] font-semibold',
                showAll
                  ? 'border-slate-200 bg-slate-50 text-slate-600'
                  : 'border-sky-100 bg-sky-50 text-sky-700',
              )}
            >
              <Link2 size={11} /> {showAll ? 'All activity' : 'Key activity'} · {visible.length} of {events.length}
            </span>
            <div className="ml-auto flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setShowAll((current) => !current)}
                className="action-secondary !py-1.5 !text-[11px]"
              >
                {showAll ? 'Show key activity' : 'Show all activity'}
              </button>
              {visible.length > 0 && (
                <button
                  type="button"
                  onClick={() =>
                    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' })
                  }
                  className="action-secondary !py-1.5 !text-[11px]"
                >
                  <ArrowDown size={13} /> Jump to latest
                </button>
              )}
              {onExport && (
                <button
                  type="button"
                  onClick={onExport}
                  title="Downloads the complete log, including routine steps"
                  className="action-secondary !py-1.5 !text-[11px]"
                >
                  <Download size={13} /> Download CSV
                </button>
              )}
            </div>
          </div>
        </header>

        <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6">
          {loading ? (
            <p className="py-12 text-center text-[12px] text-slate-400">Loading activity…</p>
          ) : ordered.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <FileText size={22} className="mx-auto text-slate-300" />
              <p className="mt-3 text-[13px] font-bold text-slate-700">No activity recorded yet</p>
              <p className="mt-1 text-[11.5px] text-slate-400">Events appear here as the invoice moves through the pipeline.</p>
            </div>
          ) : visible.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <History size={22} className="mx-auto text-slate-300" />
              <p className="mt-3 text-[13px] font-bold text-slate-700">Only routine steps so far</p>
              <p className="mt-1 text-[11.5px] text-slate-400">Nothing notable has happened yet in this view.</p>
              <button
                type="button"
                onClick={() => setShowAll(true)}
                className="action-secondary mx-auto mt-4 !py-1.5 !text-[11px]"
              >
                Show all activity
              </button>
            </div>
          ) : (
            <>
              <p className="mb-3 flex items-center gap-2 pl-7 text-[10.5px] font-semibold text-slate-400">
                <span className="h-1.5 w-1.5 rounded-full bg-sky-500" />
                {showAll ? 'First recorded activity' : 'First key activity'}
              </p>
              <ol className="relative space-y-2.5">
                {visible.map((event, index) => {
                  const meta = metaFor(event)
                  const style = TONE_STYLES[meta.tone]
                  const Icon = meta.icon
                  const rows = payloadRows(event)
                  const message = (() => {
                    try {
                      return event.payloadJson ? (JSON.parse(event.payloadJson) as { message?: string }).message ?? null : null
                    } catch {
                      return null
                    }
                  })()
                  const transition = event.fromStatus && event.toStatus
                    ? `${auditStatusLabel(event.fromStatus)} → ${auditStatusLabel(event.toStatus)}`
                    : event.toStatus
                      ? auditStatusLabel(event.toStatus)
                      : null

                  return (
                    <li key={event.id} className="relative pl-7">
                      <span className={cn('absolute left-[7px] top-4 h-2.5 w-2.5 rounded-full ring-4 ring-[#FAFAF8]', style.dot)} />
                      {index < visible.length - 1 && (
                        <>
                          <span className="absolute bottom-[-10px] left-[11px] top-6 w-px bg-slate-200" aria-hidden="true" />
                          <ChevronDown
                            size={10}
                            strokeWidth={3}
                            className="absolute bottom-[-13px] left-[7px] text-slate-300"
                            aria-hidden="true"
                          />
                        </>
                      )}
                      <article className="min-w-0 max-w-full rounded-2xl border border-slate-200 bg-white p-3.5 shadow-[0_1px_2px_rgba(15,23,42,0.03)]">
                      <div className="flex items-start gap-2.5">
                        <span className={cn('flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border', style.chip)}>
                          <Icon size={14} strokeWidth={2.1} />
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                            <p className="text-[12.5px] font-bold text-slate-900">{meta.label}</p>
                            <time dateTime={event.createdAt} title={fullTimestamp(event.createdAt)} className="text-[10.5px] text-slate-400">
                              {timeAgo(event.createdAt)}
                            </time>
                          </div>
                          <p className="mt-0.5 text-[11px] text-slate-500">
                            {actorName(event, users)}
                            {event.role ? ` · ${auditPretty(event.role)}` : ''}
                            {event.actingFor ? ` · on behalf of ${users.find((user) => user.id === event.actingFor)?.fullName ?? event.actingFor}` : ''}
                          </p>
                        </div>
                      </div>

                      <div className="mt-2.5 flex flex-wrap gap-1.5">
                        {event.node && (
                          <span className="inline-flex items-center rounded-md bg-slate-50 px-2 py-1 text-[10.5px] font-medium text-slate-600">
                            {auditNodeLabel(event.node)}
                          </span>
                        )}
                        {transition && (
                          <span className="inline-flex items-center rounded-md bg-slate-50 px-2 py-1 text-[10.5px] font-medium text-slate-600">
                            {transition}
                          </span>
                        )}
                        {event.invoiceId && (
                          <span className="inline-flex items-center rounded-md bg-slate-50 px-2 py-1 font-mono text-[10px] text-slate-500">
                            {event.invoiceId}
                          </span>
                        )}
                      </div>

                      {message && (
                        <p className="mt-2.5 max-w-full break-words text-[11.5px] leading-relaxed text-slate-700 [overflow-wrap:anywhere]">
                          {message}
                        </p>
                      )}

                      {rows.length > 0 && (
                        <dl className="mt-2.5 grid min-w-0 max-w-full grid-cols-[minmax(88px,auto)_minmax(0,1fr)] gap-x-3 gap-y-1 border-t border-slate-100 pt-2.5">
                          {rows.map((row) => (
                            <div key={row.label} className="contents">
                              <dt className="min-w-0 break-words text-[10.5px] font-semibold text-slate-400">{row.label}</dt>
                              <dd className="min-w-0 break-all text-[10.5px] leading-relaxed text-slate-600 [overflow-wrap:anywhere]">
                                {row.value}
                              </dd>
                            </div>
                          ))}
                        </dl>
                      )}

                      <details className="group mt-2.5 min-w-0 border-t border-slate-100 pt-2">
                        <summary className="cursor-pointer list-none text-[10.5px] font-semibold text-slate-400 transition hover:text-slate-600">
                          Technical details
                        </summary>
                        <div className="mt-2 max-w-full overflow-hidden rounded-lg bg-slate-50 p-2.5 font-mono text-[10px] leading-relaxed text-slate-500">
                          <div className="break-all">Event #{event.id} · {event.action}</div>
                          <div className="break-all">Hash {event.hash ?? '—'}</div>
                          <div className="break-all">Previous {event.prevHash ?? '—'}</div>
                          {payloadText(event) && <pre className="mt-2 max-w-full whitespace-pre-wrap break-all text-slate-500">{payloadText(event)}</pre>}
                        </div>
                      </details>
                    </article>
                  </li>
                )
                })}
              </ol>
            </>
          )}
          {visible.length > 0 && (
            <p className="mt-3 flex items-center gap-2 pl-7 text-[10.5px] font-semibold text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> Latest activity
            </p>
          )}
        </div>
      </aside>
    </div>
  )
}
