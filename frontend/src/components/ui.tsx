import type { ReactNode } from 'react'
import { AlertCircle, Clock, ShieldAlert } from 'lucide-react'
import type { InvoiceFlag, InvoiceStatus, RiskTone } from '../types'
import { cn } from '../lib/cn'

const avatarSizes = {
  xs: 'h-5 w-5 text-[8px]',
  sm: 'h-7 w-7 text-[10px]',
  md: 'h-9 w-9 text-[11px]',
  lg: 'h-10 w-10 text-xs',
} as const

export function Avatar({
  initials,
  className,
  size = 'md',
  ring = false,
}: {
  initials: string
  className: string
  size?: keyof typeof avatarSizes
  ring?: boolean
}) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full font-bold tracking-wide',
        avatarSizes[size],
        className,
        ring && 'ring-2 ring-white',
      )}
      aria-hidden="true"
    >
      {initials}
    </span>
  )
}

export function Tag({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-md border border-slate-200 bg-white px-1.5 py-0.5 text-[10.5px] font-medium leading-4 text-slate-500',
        className,
      )}
    >
      {children}
    </span>
  )
}

const flagStyles: Record<InvoiceFlag, { label: string; className: string; Icon: typeof Clock }> = {
  fraud: {
    label: 'Fraud Alert',
    className: 'border-rose-100 bg-rose-50 text-rose-600',
    Icon: ShieldAlert,
  },
  critical: {
    label: 'Critical',
    className: 'border-amber-200 bg-amber-50 text-amber-700',
    Icon: AlertCircle,
  },
  overdue: {
    label: 'Overdue',
    className: 'border-orange-100 bg-orange-50 text-orange-600',
    Icon: Clock,
  },
}

export function FlagBadge({ flag }: { flag: InvoiceFlag }) {
  const { label, className, Icon } = flagStyles[flag]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10.5px] font-semibold leading-4',
        className,
      )}
    >
      <Icon size={10} strokeWidth={2.4} aria-hidden="true" />
      {label}
    </span>
  )
}

const statusStyles: Record<InvoiceStatus, { className: string; dot: string }> = {
  'Needs review': { className: 'border-amber-200 bg-amber-50 text-amber-700', dot: 'bg-amber-500' },
  'Pending approval': { className: 'border-sky-200 bg-sky-50 text-sky-700', dot: 'bg-sky-500' },
  Approved: {
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    dot: 'bg-emerald-500',
  },
  Scheduled: {
    className: 'border-violet-200 bg-violet-50 text-violet-700',
    dot: 'bg-violet-500',
  },
  Paid: { className: 'border-emerald-200 bg-emerald-50 text-emerald-700', dot: 'bg-emerald-500' },
  'On hold': { className: 'border-rose-200 bg-rose-50 text-rose-700', dot: 'bg-rose-500' },
}

export function StatusPill({
  status,
  size = 'sm',
  className,
}: {
  status: InvoiceStatus
  size?: 'sm' | 'md'
  className?: string
}) {
  const style = statusStyles[status]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border font-semibold',
        size === 'sm' ? 'px-2 py-0.5 text-[10.5px]' : 'px-2.5 py-1 text-[11.5px]',
        style.className,
        className,
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', style.dot)} aria-hidden="true" />
      {status}
    </span>
  )
}

const riskStyles: Record<RiskTone, { className: string; dot: string }> = {
  low: { className: 'bg-emerald-50 text-emerald-700 border-emerald-100', dot: 'bg-emerald-500' },
  medium: { className: 'bg-amber-50 text-amber-700 border-amber-100', dot: 'bg-amber-500' },
  high: { className: 'bg-rose-50 text-rose-700 border-rose-100', dot: 'bg-rose-500' },
}

export function RiskPill({
  tone,
  label,
  score,
}: {
  tone: RiskTone
  label: string
  score: number
}) {
  const style = riskStyles[tone]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10.5px] font-semibold',
        style.className,
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', style.dot)} aria-hidden="true" />
      {label}
      <span className="font-medium opacity-60">{score}/100</span>
    </span>
  )
}

export function IconButton({
  label,
  onClick,
  children,
  className,
}: {
  label: string
  onClick?: () => void
  children: ReactNode
  className?: string
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className={cn(
        'inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 active:scale-95',
        className,
      )}
    >
      {children}
    </button>
  )
}
