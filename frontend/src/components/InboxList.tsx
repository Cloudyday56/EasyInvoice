import { useEffect, useRef, useState } from 'react'
import { CreditCard, Inbox, Landmark, Menu, Plus, Search, SearchX, X } from 'lucide-react'
import type { Invoice } from '../types'
import type { FilterId } from '../types'
import { filterLabels } from '../lib/filters'
import { cn } from '../lib/cn'
import { formatMoney } from '../lib/format'
import { Avatar, FlagBadge, IconButton } from './ui'

interface InboxListProps {
  invoices: Invoice[]
  selectedId: string | null
  activeFilter: FilterId
  query: string
  onQueryChange: (query: string) => void
  onSelect: (id: string) => void
  onOpenSidebar: () => void
  onUpload: () => void
  onClearFilters: () => void
}

export function InboxList({
  invoices,
  selectedId,
  activeFilter,
  query,
  onQueryChange,
  onSelect,
  onOpenSidebar,
  onUpload,
  onClearFilters,
}: InboxListProps) {
  const [searchOpen, setSearchOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (searchOpen) inputRef.current?.focus()
  }, [searchOpen])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const isTyping =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable

      if (event.key === '/' && !isTyping) {
        event.preventDefault()
        setSearchOpen(true)
      }

      if (event.key === 'Escape' && searchOpen) {
        setSearchOpen(false)
        onQueryChange('')
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [searchOpen, onQueryChange])

  return (
    <section
      className="flex h-full w-full shrink-0 flex-col border-r border-line bg-white lg:w-[400px] xl:w-[380px] 2xl:w-[420px]"
      aria-label="Invoice inbox"
    >
      <header className="shrink-0 border-b border-line/80 px-4 pb-3 pt-4 sm:px-5 sm:pt-5">
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              onClick={onOpenSidebar}
              aria-label="Open menu"
              title="Open menu"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 xl:hidden"
            >
              <Menu size={18} />
            </button>

            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-sky-50 text-sky-600">
              <Inbox size={17} strokeWidth={2} />
            </div>

            <h1 className="truncate text-[20px] font-extrabold tracking-[-0.02em] text-slate-900">
              Inbox
            </h1>
            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-slate-100 px-1.5 text-[10.5px] font-bold tabular-nums text-slate-500">
              {invoices.length}
            </span>
          </div>

          <div className="flex shrink-0 items-center gap-0.5">
            <IconButton
              label="Search invoices"
              onClick={() => setSearchOpen((open) => !open)}
              className={cn(searchOpen && 'bg-slate-100 text-slate-700')}
            >
              <Search size={17} />
            </IconButton>
            <IconButton label="Upload invoice" onClick={onUpload} className="xl:hidden">
              <Plus size={18} />
            </IconButton>
          </div>
        </div>

        <div className="mt-1.5 flex items-center justify-between pl-0 text-[11px] font-medium text-slate-400 xl:pl-[42px]">
          <span className="truncate">
            {query.trim()
              ? `Searching all queues · ${invoices.length} result${invoices.length === 1 ? '' : 's'}`
              : filterLabels[activeFilter]}
          </span>
          <span className="hidden shrink-0 items-center gap-1.5 sm:flex">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Synced 1m ago
          </span>
        </div>

        {searchOpen && (
          <div className="animate-fade-up relative mt-3">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              ref={inputRef}
              type="search"
              value={query}
              onChange={(event) => onQueryChange(event.target.value)}
              placeholder="Search supplier, invoice #, amount…"
              className="h-9 w-full rounded-xl border border-slate-200 bg-slate-50/80 pl-9 pr-9 text-[12.5px] font-medium text-slate-700 placeholder:font-normal placeholder:text-slate-400 focus:border-sky-300 focus:bg-white focus:outline-none focus:ring-2 focus:ring-sky-100 [&::-webkit-search-cancel-button]:hidden"
            />
            <button
              type="button"
              onClick={() => {
                onQueryChange('')
                setSearchOpen(false)
              }}
              aria-label="Close search"
              className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
            >
              <X size={14} />
            </button>
          </div>
        )}
      </header>

      <div className="scrollbar-thin flex-1 overflow-y-auto px-3 pb-5 pt-3">
        {invoices.length === 0 ? (
          <div className="flex h-full min-h-[320px] flex-col items-center justify-center px-6 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-50 text-slate-300 ring-1 ring-slate-100">
              <SearchX size={24} strokeWidth={1.7} />
            </div>
            <p className="mt-4 text-[14px] font-bold text-slate-800">No invoices found</p>
            <p className="mt-1 max-w-[220px] text-[12px] leading-relaxed text-slate-400">
              Try a different supplier, invoice number, or clear the active filter.
            </p>
            <button
              type="button"
              onClick={onClearFilters}
              className="mt-4 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-[11.5px] font-semibold text-slate-600 shadow-sm transition hover:border-slate-300 hover:text-slate-900"
            >
              Clear filters
            </button>
          </div>
        ) : (
          <div className="space-y-2.5">
            {invoices.map((invoice) => (
              <InvoiceCard
                key={invoice.id}
                invoice={invoice}
                selected={invoice.id === selectedId}
                onSelect={() => onSelect(invoice.id)}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

function InvoiceCard({
  invoice,
  selected,
  onSelect,
}: {
  invoice: Invoice
  selected: boolean
  onSelect: () => void
}) {
  const needsAttention = invoice.status === 'Needs review' || invoice.status === 'On hold'
  const dotClass = invoice.flags.includes('fraud') ? 'bg-rose-500' : 'bg-sky-500'
  const priorityFlag = invoice.flags.includes('fraud')
    ? 'fraud'
    : invoice.flags.includes('critical')
      ? 'critical'
      : invoice.flags.includes('overdue')
        ? 'overdue'
        : null
  const PaymentIcon = invoice.payment.kind === 'card' ? CreditCard : Landmark

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        'group w-full rounded-2xl border px-3.5 py-3 text-left transition duration-200',
        selected
          ? 'border-sky-200 bg-[#eff8fe] shadow-[0_10px_30px_-18px_rgba(14,165,233,0.55)]'
          : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/50 hover:shadow-sm',
      )}
    >
      <div className="flex items-center gap-2">
        <Avatar initials={invoice.supplierInitials} className={invoice.avatarClass} size="sm" />
        <span
          className={cn(
            'truncate text-[12px] font-bold',
            selected ? 'text-slate-900' : 'text-slate-700',
          )}
        >
          {invoice.supplier}
        </span>
        {needsAttention && (
          <span className={cn('h-1.5 w-1.5 shrink-0 rounded-full', dotClass)} aria-hidden="true" />
        )}
        <span className="ml-auto shrink-0 text-[10.5px] font-medium text-slate-400">
          {invoice.received}
        </span>
      </div>

      <h3
        className={cn(
          'mt-2 line-clamp-2 text-[13.5px] font-semibold leading-[1.45] tracking-[-0.005em]',
          selected ? 'text-slate-900' : 'text-slate-800',
        )}
      >
        {invoice.subject}
      </h3>

      <div className="mt-2 flex items-center justify-between gap-2">
        <span className="truncate rounded-md border border-slate-200/90 bg-white/80 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-slate-500">
          {invoice.invoiceNumber}
        </span>
        <span className="shrink-0 text-[14px] font-extrabold tabular-nums tracking-[-0.02em] text-slate-900">
          {formatMoney(invoice.amount, invoice.currency)}
        </span>
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <span
          className={cn(
            'inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[9.5px] font-extrabold uppercase tracking-[0.05em]',
            invoice.payment.kind === 'card'
              ? 'border-sky-100 bg-sky-50 text-sky-700'
              : 'border-slate-200 bg-slate-50 text-slate-500',
          )}
          title={invoice.payment.label}
        >
          <PaymentIcon size={10} strokeWidth={2.2} />
          {invoice.payment.kind === 'card'
            ? `Card ${invoice.payment.detail}`.trim()
            : 'EFT'}
        </span>

        {priorityFlag && <FlagBadge flag={priorityFlag} />}

        <span className="ml-auto truncate text-[10px] font-medium text-slate-400">
          {invoice.requester ? invoice.requester.name.split(' ')[0] : 'Unassigned'}
        </span>
      </div>
    </button>
  )
}
