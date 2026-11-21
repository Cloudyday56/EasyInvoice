import {
  CheckCircle2,
  CreditCard,
  Inbox,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  ShieldAlert,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { FilterId } from '../types'
import { filterLabels } from '../lib/filters'
import { cn } from '../lib/cn'
import { Avatar } from './ui'

type NavTone = 'neutral' | 'alert' | 'success'

interface NavItem {
  id: FilterId
  label: string
  icon: LucideIcon
  tone: NavTone
}

const inboxNav: NavItem[] = [
  { id: 'action', label: filterLabels.action, icon: Inbox, tone: 'neutral' },
  { id: 'holds', label: filterLabels.holds, icon: ShieldAlert, tone: 'alert' },
  { id: 'paid', label: filterLabels.paid, icon: CheckCircle2, tone: 'success' },
]

interface SidebarProps {
  collapsed: boolean
  mobileOpen: boolean
  activeFilter: FilterId
  counts: Record<FilterId, number>
  attentionCount: number
  onToggleCollapse: () => void
  onCloseMobile: () => void
  onFilterChange: (filter: FilterId) => void
  onUpload: () => void
}

export function Sidebar({
  collapsed,
  mobileOpen,
  activeFilter,
  counts,
  attentionCount,
  onToggleCollapse,
  onCloseMobile,
  onFilterChange,
  onUpload,
}: SidebarProps) {
  const compact = collapsed && !mobileOpen

  return (
    <>
      <div
        className={cn(
          'fixed inset-0 z-40 bg-slate-900/25 backdrop-blur-[2px] transition-opacity xl:hidden',
          mobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={onCloseMobile}
        aria-hidden="true"
      />

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-[288px] shrink-0 flex-col border-r border-line bg-[#f7f9fc] transition-[width,transform] duration-300 ease-out xl:static xl:translate-x-0 xl:shadow-none',
          compact ? 'xl:w-[88px]' : 'xl:w-[280px]',
          mobileOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full',
        )}
        aria-label="Main navigation"
      >
        {compact && (
          <button
            type="button"
            onClick={onToggleCollapse}
            title="Expand menu"
            aria-label="Expand menu"
            className="absolute -right-3 top-8 z-10 hidden h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-400 shadow-sm transition hover:text-slate-700 xl:flex"
          >
            <PanelLeftOpen size={13} />
          </button>
        )}

        <div
          className={cn(
            'flex items-center gap-2.5 px-4 pt-5',
            compact && 'justify-center px-0',
          )}
        >
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-sky-400 to-blue-600 text-white shadow-lg shadow-sky-500/25">
            <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" aria-hidden="true">
              <path
                d="M6 3.5h12v17l-3-2-3 2-3-2-3 2v-17Z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
              />
              <path
                d="M9 8.5h6M9 12.5h6"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
              />
            </svg>
          </div>

          {!compact && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-[16px] font-extrabold tracking-[-0.02em] text-slate-900">
                EasyInvoice
              </p>
              <p className="truncate text-[10.5px] font-medium text-slate-400">AP automation</p>
            </div>
          )}

          {!compact && (
            <button
              type="button"
              onClick={onToggleCollapse}
              title="Collapse menu"
              aria-label="Collapse menu"
              className="hidden h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-white hover:text-slate-700 xl:flex"
            >
              <PanelLeftClose size={17} />
            </button>
          )}

          <button
            type="button"
            onClick={onCloseMobile}
            title="Close menu"
            aria-label="Close menu"
            className="ml-auto flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-white hover:text-slate-700 xl:hidden"
          >
            <X size={17} />
          </button>
        </div>

        <div
          className={cn(
            'mx-3 mt-5 flex items-center gap-3 rounded-2xl border border-slate-200/80 bg-white p-2.5 shadow-sm shadow-slate-200/40',
            compact && 'mx-2 justify-center border-transparent bg-transparent p-0 shadow-none',
          )}
        >
          <Avatar
            initials="HL"
            size="lg"
            className="bg-gradient-to-br from-sky-400 to-blue-600 text-white"
            ring={!compact}
          />
          {!compact && (
            <div className="min-w-0">
              <p className="truncate text-[12.5px] font-bold text-slate-800">
                Harborline Logistics
              </p>
              <p className="truncate text-[10.5px] font-medium text-slate-400">
                AP Team · Toronto
              </p>
            </div>
          )}
        </div>

        <div className={cn('mt-3 px-3', compact && 'px-2')}>
          <button
            type="button"
            onClick={onUpload}
            title="Upload invoice"
            className={cn(
              'flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-sky-500 to-blue-600 text-[13px] font-semibold text-white shadow-lg shadow-sky-500/25 transition hover:brightness-105 active:scale-[0.98]',
              compact && 'px-0',
            )}
          >
            <Plus size={17} strokeWidth={2.4} />
            {!compact && 'Upload invoice'}
          </button>
        </div>

        <nav className="scrollbar-thin mt-6 flex-1 overflow-y-auto px-3 pb-4">
          {!compact && (
            <p className="px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
              Queues
            </p>
          )}

          <div className={cn('mt-2 space-y-0.5', compact && 'mt-0')}>
            {inboxNav.map((item) => {
              const active = activeFilter === item.id
              const count = counts[item.id]
              const Icon = item.icon

              return (
                <button
                  key={item.id}
                  type="button"
                  title={compact ? item.label : undefined}
                  onClick={() => onFilterChange(item.id)}
                  className={cn(
                    'group relative flex h-10 w-full items-center gap-3 rounded-xl px-3 text-[13px] font-medium transition',
                    active
                      ? 'bg-sky-50 text-sky-700 shadow-[inset_0_0_0_1px_rgba(14,165,233,0.14)]'
                      : 'text-slate-600 hover:bg-white hover:text-slate-900',
                    compact && 'justify-center px-0',
                  )}
                  aria-current={active ? 'page' : undefined}
                >
                  <Icon
                    size={18}
                    strokeWidth={1.9}
                    className={cn(
                      'shrink-0 transition',
                      active ? 'text-sky-600' : 'text-slate-400 group-hover:text-slate-600',
                    )}
                  />
                  {!compact && <span className="flex-1 truncate text-left">{item.label}</span>}
                  {!compact && count > 0 && (
                    <span
                      className={cn(
                        'inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[10.5px] font-bold tabular-nums',
                        item.tone === 'alert'
                          ? 'bg-rose-500 text-white shadow-sm shadow-rose-500/30'
                          : item.tone === 'success'
                            ? 'bg-emerald-100 text-emerald-700'
                            : active
                              ? 'bg-white text-sky-600'
                              : 'bg-slate-200/80 text-slate-500',
                      )}
                    >
                      {count}
                    </span>
                  )}
                  {compact && count > 0 && (
                    <span
                      className={cn(
                        'absolute right-2.5 top-2 h-1.5 w-1.5 rounded-full',
                        item.tone === 'alert'
                          ? 'bg-rose-500'
                          : item.tone === 'success'
                            ? 'bg-emerald-500'
                            : 'bg-sky-400',
                      )}
                    />
                  )}
                </button>
              )
            })}
          </div>

          {!compact ? (
            <div className="mt-6 rounded-2xl border border-sky-100 bg-sky-50/60 p-3">
              <div className="flex items-center gap-2">
                <span className="flex h-6 w-6 items-center justify-center rounded-md bg-white text-sky-600 shadow-sm ring-1 ring-sky-100">
                  <CreditCard size={13} strokeWidth={2.2} />
                </span>
                <p className="text-[9.5px] font-extrabold uppercase tracking-[0.15em] text-sky-700/80">
                  Payment rail
                </p>
              </div>
              <p className="mt-2 text-[11.5px] font-bold text-slate-700">Brim corporate card</p>
              <p className="mt-0.5 flex items-center gap-1.5 text-[10px] font-medium text-slate-400">
                •• 4821
                <span className="h-1 w-1 rounded-full bg-slate-300" />
                Active
                <span className="ml-auto h-1.5 w-1.5 rounded-full bg-emerald-500" />
              </p>
            </div>
          ) : (
            <div className="mt-5 flex justify-center">
              <span
                title="Brim corporate card •• 4821"
                className="flex h-9 w-9 items-center justify-center rounded-xl border border-sky-100 bg-sky-50 text-sky-600"
              >
                <CreditCard size={16} strokeWidth={2} />
              </span>
            </div>
          )}
        </nav>

        {!compact && (
          <div className="px-3 pb-3">
            <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-3">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
                </span>
                <p className="text-[11px] font-bold text-emerald-800">Pipeline healthy</p>
              </div>
              <p className="mt-1.5 text-[10.5px] leading-relaxed text-emerald-700/80">
                9 invoices this week · {attentionCount} need attention
              </p>
            </div>
          </div>
        )}

        <div className={cn('border-t border-slate-200/80 p-3', compact && 'px-2')}>
          <div
            className={cn(
              'flex items-center gap-2.5 rounded-xl p-2 transition hover:bg-white',
              compact && 'justify-center p-0',
            )}
          >
            <div className="relative">
              <Avatar initials="MC" className="bg-slate-800 text-white" size="md" />
              <span className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-[#f7f9fc] bg-emerald-500" />
            </div>
            {!compact && (
              <div className="min-w-0 flex-1">
                <p className="truncate text-[12px] font-bold text-slate-800">Maya Chen</p>
                <p className="truncate text-[10.5px] text-slate-400">AP Clerk · Admin</p>
              </div>
            )}
          </div>
        </div>
      </aside>
    </>
  )
}
