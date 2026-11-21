import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2 } from 'lucide-react'
import type { FilterId, Invoice } from './types'
import { invoices as seedInvoices } from './data/invoices'

const isHold = (invoice: Invoice) =>
  invoice.status === 'On hold' || invoice.flags.includes('fraud')
import { formatMoney } from './lib/format'
import { cn } from './lib/cn'
import { Sidebar } from './components/Sidebar'
import { InboxList } from './components/InboxList'
import { PreviewPanel } from './components/PreviewPanel'
import { UploadModal } from './components/UploadModal'

type MobileView = 'inbox' | 'preview'

export default function App() {
  const [items, setItems] = useState<Invoice[]>(seedInvoices)
  const [activeFilter, setActiveFilter] = useState<FilterId>('action')
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(seedInvoices[0]?.id ?? null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [mobileView, setMobileView] = useState<MobileView>('inbox')
  const [uploadOpen, setUploadOpen] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const [toastBottom, setToastBottom] = useState(20)
  const toastTimer = useRef<number | undefined>(undefined)

  const notify = useCallback((message: string) => {
    setToast(message)
    if (toastTimer.current) window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => setToast(null), 3800)
  }, [])

  useEffect(() => {
    return () => {
      if (toastTimer.current) window.clearTimeout(toastTimer.current)
    }
  }, [])

  useEffect(() => {
    const updateToastPosition = () => {
      const footer = document.querySelector<HTMLElement>('[data-preview-footer]')
      if (!footer) {
        setToastBottom(20)
        return
      }

      const rect = footer.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) {
        setToastBottom(20)
        return
      }

      setToastBottom(Math.max(20, Math.round(window.innerHeight - rect.top + 10)))
    }

    updateToastPosition()

    const footer = document.querySelector<HTMLElement>('[data-preview-footer]')
    const observer = new ResizeObserver(updateToastPosition)
    if (footer) observer.observe(footer)

    window.addEventListener('resize', updateToastPosition)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', updateToastPosition)
    }
  }, [])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      if (uploadOpen) setUploadOpen(false)
      if (mobileSidebarOpen) setMobileSidebarOpen(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [uploadOpen, mobileSidebarOpen])

  const counts = useMemo<Record<FilterId, number>>(
    () => ({
      action: items.filter((invoice) => invoice.status !== 'Paid' && !isHold(invoice)).length,
      holds: items.filter(isHold).length,
      paid: items.filter((invoice) => invoice.status === 'Paid').length,
    }),
    [items],
  )

  const visibleInvoices = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()

    return items
      .filter((invoice) => {
        if (normalizedQuery) {
          const haystack = [
            invoice.supplier,
            invoice.supplierEmail,
            invoice.subject,
            invoice.invoiceNumber,
            invoice.requester?.name ?? '',
            invoice.tags.join(' '),
            formatMoney(invoice.amount, invoice.currency),
            String(invoice.amount),
          ]
            .join(' ')
            .toLowerCase()

          return haystack.includes(normalizedQuery)
        }

        return activeFilter === 'action'
          ? invoice.status !== 'Paid' && !isHold(invoice)
          : activeFilter === 'holds'
            ? isHold(invoice)
            : invoice.status === 'Paid'
      })
      .sort((a, b) => a.receivedAt - b.receivedAt)
  }, [items, activeFilter, query])

  useEffect(() => {
    if (visibleInvoices.length === 0) {
      setSelectedId(null)
      return
    }

    if (!selectedId || !visibleInvoices.some((invoice) => invoice.id === selectedId)) {
      setSelectedId(visibleInvoices[0].id)
    }
  }, [visibleInvoices, selectedId])

  const selectedInvoice = items.find((invoice) => invoice.id === selectedId) ?? null

  const updateInvoice = (id: string, patch: Partial<Invoice>) => {
    setItems((current) =>
      current.map((invoice) => (invoice.id === id ? { ...invoice, ...patch } : invoice)),
    )
  }

  const handlePrimaryAction = () => {
    if (!selectedInvoice) return

    if (selectedInvoice.status === 'Paid') {
      notify(
        `Paid · ${selectedInvoice.payment.kind === 'card' ? 'card' : 'EFT'} receipt is available in the audit trail`,
      )
      return
    }

    if (selectedInvoice.flags.includes('fraud')) {
      notify('Fraud hold opened · verify the bank change by callback before releasing payment')
      return
    }

    if (selectedInvoice.status === 'On hold' || selectedInvoice.flags.includes('critical')) {
      notify('Exception queue opened · correct the invoice or override with a written reason')
      return
    }

    if (selectedInvoice.status === 'Approved' || selectedInvoice.status === 'Scheduled') {
      updateInvoice(selectedInvoice.id, {
        status: 'Paid',
        pipeline: { node: 'N10', label: 'Payment settled', progress: 100 },
      })
      notify(
        `${selectedInvoice.supplier} payment released · ${
          selectedInvoice.payment.kind === 'card' ? 'card charge' : 'EFT'
        } settlement simulated`,
      )
      return
    }

    updateInvoice(selectedInvoice.id, {
      status: 'Approved',
      approvedBy: 'Maya Chen',
      pipeline: {
        node: 'N9',
        label: selectedInvoice.payment.kind === 'card' ? 'Card payment scheduled' : 'EFT payment scheduled',
        progress: 90,
      },
    })
    notify(
      `Approved · ${formatMoney(selectedInvoice.amount, selectedInvoice.currency)} ${
        selectedInvoice.payment.kind === 'card' ? 'card payment' : 'EFT'
      } scheduled for ${selectedInvoice.dueDate}`,
    )
  }

  const handleFlag = () => {
    if (!selectedInvoice) return

    if (selectedInvoice.status === 'Paid') {
      notify('Paid invoices are locked and cannot be flagged.')
      return
    }

    if (isHold(selectedInvoice)) {
      notify('Already in Fraud & holds · add a note in the audit log instead.')
      return
    }

    updateInvoice(selectedInvoice.id, {
      status: 'On hold',
      pipeline: { ...selectedInvoice.pipeline, label: 'Flagged for review' },
    })
    notify('Moved to Fraud & holds · a written reason will be required in the audit log')
  }

  const handleAssign = (name: string) => {
    notify(`Assigned to ${name} · notification sent`)
  }

  const handleSendReply = (message: string) => {
    if (!selectedInvoice) return
    notify(`Reply sent to ${selectedInvoice.supplier} · ${message.length} characters logged`)
  }

  const handleUpload = (fileName: string | null) => {
    setUploadOpen(false)
    notify(
      fileName
        ? `${fileName} queued for intake · N1 received`
        : 'Sample invoice queued for intake · N1 received',
    )
  }

  const clearFilters = () => {
    setActiveFilter('action')
    setQuery('')
  }

  const selectInvoice = (id: string) => {
    setSelectedId(id)
    setMobileView('preview')
  }

  return (
    <div className="h-full w-full overflow-hidden bg-[radial-gradient(130%_130%_at_0%_0%,#e6f4fd_0%,#edf2f8_48%,#e9eef6_100%)] p-0 lg:p-3 xl:p-4">
      <div className="relative mx-auto flex h-full w-full max-w-[1840px] overflow-hidden bg-white lg:rounded-[26px] lg:border lg:border-white/70 lg:shadow-[0_28px_90px_-30px_rgba(15,23,42,0.32)]">
        <Sidebar
          collapsed={sidebarCollapsed}
          mobileOpen={mobileSidebarOpen}
          activeFilter={activeFilter}
          counts={counts}
          attentionCount={counts.holds}
          onToggleCollapse={() => setSidebarCollapsed((collapsed) => !collapsed)}
          onCloseMobile={() => setMobileSidebarOpen(false)}
          onFilterChange={(filter) => {
            setActiveFilter(filter)
            setMobileSidebarOpen(false)
          }}
          onUpload={() => {
            setUploadOpen(true)
            setMobileSidebarOpen(false)
          }}
        />

        <main className="flex min-w-0 flex-1">
          <div
            className={cn(
              'h-full w-full lg:w-auto lg:shrink-0',
              mobileView === 'preview' ? 'hidden lg:block' : 'block',
            )}
          >
            <InboxList
              invoices={visibleInvoices}
              selectedId={selectedId}
              activeFilter={activeFilter}
              query={query}
              onQueryChange={setQuery}
              onSelect={selectInvoice}
              onOpenSidebar={() => setMobileSidebarOpen(true)}
              onUpload={() => setUploadOpen(true)}
              onClearFilters={clearFilters}
            />
          </div>

          <div
            className={cn(
              'flex h-full min-w-0 flex-1',
              mobileView === 'inbox' ? 'hidden lg:flex' : 'flex',
            )}
          >
            <PreviewPanel
              invoice={selectedInvoice}
              onBack={() => setMobileView('inbox')}
              onPrimaryAction={handlePrimaryAction}
              onAssign={handleAssign}
              onFlag={handleFlag}
              onReply={handleSendReply}
              onDownload={() =>
                notify(
                  selectedInvoice
                    ? `Downloading ${selectedInvoice.attachment.name}…`
                    : 'No attachment selected.',
                )
              }
              onAudit={() => notify('Audit trail · 7 events · hash chain verified')}
            />
          </div>
        </main>
      </div>

      <UploadModal open={uploadOpen} onClose={() => setUploadOpen(false)} onSubmit={handleUpload} />

      {toast && (
        <div
          role="status"
          aria-live="polite"
          style={{ bottom: toastBottom }}
          className="animate-toast pointer-events-none fixed right-5 z-[70] flex max-w-[calc(100vw-2.5rem)] select-none items-center gap-2.5 rounded-2xl bg-slate-900 px-4 py-2.5 text-white shadow-2xl shadow-slate-900/30"
        >
          <CheckCircle2 size={16} className="shrink-0 text-emerald-400" />
          <p className="text-[12px] font-semibold leading-snug">{toast}</p>
        </div>
      )}
    </div>
  )
}
