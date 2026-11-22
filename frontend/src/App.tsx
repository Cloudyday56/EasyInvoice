import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { FilterId, Invoice } from './types'
import { api, ApiError, getStoredUserId, setCurrentUserId } from './api/client'
import type { AuditEventDto, InvoiceDetail, InvoiceDto, Lookups, UserDto, VerifyResult } from './api/client'
import { isOnHold, isTerminal, toInvoice } from './api/adapt'
import { formatMoney } from './lib/format'
import { cn } from './lib/cn'
import { Sidebar } from './components/Sidebar'
import { InboxList } from './components/InboxList'
import { PreviewPanel } from './components/PreviewPanel'
import type { PanelAction } from './components/PreviewPanel'
import { UploadModal } from './components/UploadModal'
import { ReasonModal } from './components/ReasonModal'
import { AuditTrailDrawer } from './components/AuditTrailDrawer'

type MobileView = 'inbox' | 'preview'
type ToastTone = 'success' | 'error'

const LIST_POLL_MS = 2500
const DETAIL_POLL_MS = 1200

const isHold = (invoice: Invoice) =>
  isOnHold(invoice.raw.status) || invoice.raw.status === 'IGNORED' || invoice.flags.includes('fraud')
const isClosed = (invoice: Invoice) => invoice.raw.status === 'PAID' || invoice.raw.status === 'REJECTED'
const isAction = (invoice: Invoice) => !isClosed(invoice) && !isHold(invoice)

interface ReasonRequest {
  title: string
  description: string
  confirmLabel: string
  tone: 'dark' | 'danger'
  optional?: boolean
  run: (reason: string) => Promise<void>
}

const errorMessage = (error: unknown) =>
  error instanceof ApiError ? error.message : error instanceof Error ? error.message : String(error)

export default function App() {
  const [users, setUsers] = useState<UserDto[]>([])
  const [userId, setUserId] = useState<string | null>(getStoredUserId())
  const [lookups, setLookups] = useState<Lookups | null>(null)
  const [rows, setRows] = useState<InvoiceDto[]>([])
  const [details, setDetails] = useState<Record<string, InvoiceDetail>>({})
  const [backendDown, setBackendDown] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  const [activeFilter, setActiveFilter] = useState<FilterId>('action')
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [mobileView, setMobileView] = useState<MobileView>('inbox')
  const [uploadOpen, setUploadOpen] = useState(false)
  const [reasonRequest, setReasonRequest] = useState<ReasonRequest | null>(null)
  const [toast, setToast] = useState<{ message: string; tone: ToastTone } | null>(null)
  const [toastBottom, setToastBottom] = useState(20)
  const [auditView, setAuditView] = useState<{ invoiceId?: string; title: string; subtitle: string } | null>(null)
  const [auditEvents, setAuditEvents] = useState<AuditEventDto[]>([])
  const [auditVerification, setAuditVerification] = useState<VerifyResult | null>(null)
  const [auditLoading, setAuditLoading] = useState(false)
  const toastTimer = useRef<number | undefined>(undefined)

  const currentUser = users.find((u) => u.id === userId) ?? null

  const notify = useCallback((message: string, tone: ToastTone = 'success') => {
    setToast({ message, tone })
    if (toastTimer.current) window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => setToast(null), tone === 'error' ? 6000 : 3800)
  }, [])

  useEffect(() => {
    return () => {
      if (toastTimer.current) window.clearTimeout(toastTimer.current)
    }
  }, [])

  const closeAudit = useCallback(() => {
    setAuditView(null)
    setAuditEvents([])
    setAuditVerification(null)
    setAuditLoading(false)
  }, [])

  // ---- Data loading -------------------------------------------------------

  // Users are public: the switcher needs them before anyone is "logged in".
  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const list = await api.users()
        if (cancelled) return
        setUsers(list)
        setBackendDown(false)
        setUserId((current) => {
          const valid = current && list.some((u) => u.id === current) ? current : null
          const next = valid ?? list.find((u) => u.role === 'ADMIN' && u.active)?.id ?? list[0]?.id ?? null
          setCurrentUserId(next)
          return next
        })
      } catch {
        if (cancelled) return
        setBackendDown(true)
        window.setTimeout(load, 3000)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  const refreshList = useCallback(async () => {
    try {
      const list = await api.invoices()
      setRows(list)
      setBackendDown(false)
    } catch (error) {
      if (!(error instanceof ApiError)) setBackendDown(true)
    } finally {
      setLoading(false)
      setNow(Date.now())
    }
  }, [])

  const refreshDetail = useCallback(async (id: string) => {
    try {
      const detail = await api.invoice(id)
      setDetails((current) => ({ ...current, [id]: detail }))
      // Keep the list row in sync with the fresher detail.
      setRows((current) => current.map((row) => (row.id === id ? detail.invoice : row)))
    } catch (error) {
      if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
        setDetails((current) => {
          const next = { ...current }
          delete next[id]
          return next
        })
      }
    }
  }, [])

  // When the user changes: reset and load everything they may see.
  useEffect(() => {
    if (!userId) return
    setCurrentUserId(userId)
    setLoading(true)
    setRows([])
    setDetails({})
    setSelectedId(null)
    closeAudit()
    api.lookups().then(setLookups).catch(() => undefined)
    refreshList()
  }, [userId, refreshList, closeAudit])

  // Live list.
  useEffect(() => {
    if (!userId) return
    const timer = window.setInterval(refreshList, LIST_POLL_MS)
    return () => window.clearInterval(timer)
  }, [userId, refreshList])

  // Live detail for the selected invoice (fast while it is still moving).
  const selectedRow = rows.find((row) => row.id === selectedId) ?? null
  const selectedMoving = selectedRow ? !isTerminal(selectedRow.status) : false
  useEffect(() => {
    if (!userId || !selectedId) return
    refreshDetail(selectedId)
    if (!selectedMoving) return
    const timer = window.setInterval(() => refreshDetail(selectedId), DETAIL_POLL_MS)
    return () => window.clearInterval(timer)
  }, [userId, selectedId, selectedMoving, refreshDetail])

  // ---- Layout helpers (unchanged from the static UI) ------------------------

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
  }, [selectedId])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      if (auditView) closeAudit()
      else if (reasonRequest) setReasonRequest(null)
      else if (uploadOpen) setUploadOpen(false)
      if (mobileSidebarOpen) setMobileSidebarOpen(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [auditView, closeAudit, uploadOpen, mobileSidebarOpen, reasonRequest])

  // ---- View model -----------------------------------------------------------

  const items = useMemo(
    () => rows.map((row) => toInvoice(row, { lookups, users }, details[row.id], now)),
    [rows, lookups, users, details, now],
  )

  const counts = useMemo<Record<FilterId, number>>(
    () => ({
      action: items.filter(isAction).length,
      holds: items.filter(isHold).length,
      paid: items.filter(isClosed).length,
    }),
    [items],
  )

  const visibleInvoices = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()

    return items
      .filter((invoice) => {
        if (normalizedQuery) {
          const haystack = [
            invoice.id,
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

        return activeFilter === 'action' ? isAction(invoice) : activeFilter === 'holds' ? isHold(invoice) : isClosed(invoice)
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

  // ---- Actions --------------------------------------------------------------

  const run = useCallback(
    async <T,>(work: () => Promise<T>, success: string | ((result: T) => string), invoiceId?: string) => {
      setBusy(true)
      try {
        const result = await work()
        notify(typeof success === 'function' ? success(result) : success)
        if (invoiceId) await refreshDetail(invoiceId)
        await refreshList()
        return true
      } catch (error) {
        notify(errorMessage(error), 'error')
        return false
      } finally {
        setBusy(false)
      }
    },
    [notify, refreshDetail, refreshList],
  )

  const askReason = (request: ReasonRequest) => setReasonRequest(request)

  const actionsFor = (invoice: Invoice | null): { primary: PanelAction; secondary: PanelAction[] } | null => {
    if (!invoice) return null
    const amount = formatMoney(invoice.amount, invoice.currency)
    const status = invoice.raw.status
    const id = invoice.id

    const allowed = invoice.raw.allowedActions
    const secondary: PanelAction[] = []

    if (allowed.includes('APPROVE_INVOICE')) {
      secondary.push({
        label: 'Return to AP',
        tone: 'dark',
        kind: 'return',
        onClick: () =>
          askReason({
            title: 'Return to AP',
            description: 'Send this invoice back to the AP team with a note. It leaves your approval queue.',
            confirmLabel: 'Return invoice',
            tone: 'dark',
            run: async (reason) => {
              await run(() => api.returnToAp(id, reason), 'Returned to AP · reason logged in the audit trail', id)
            },
          }),
      })
      secondary.push({
        label: 'Reject',
        tone: 'danger',
        kind: 'reject',
        onClick: () =>
          askReason({
            title: 'Reject invoice',
            description: 'The invoice will not be paid. A written reason is required and kept in the audit trail.',
            confirmLabel: 'Reject invoice',
            tone: 'danger',
            run: async (reason) => {
              await run(() => api.reject(id, reason), 'Invoice rejected', id)
            },
          }),
      })
      return {
        primary: {
          label: `Approve (${amount})`,
          tone: 'dark',
          kind: 'approve',
          disabled: busy,
          onClick: () =>
            askReason({
              title: 'Approve invoice',
              description: `Approve ${invoice.supplier} · ${amount}. A comment is optional.`,
              confirmLabel: 'Approve',
              tone: 'dark',
              optional: true,
              run: async (reason) => {
                await run(() => api.approve(id, reason || undefined), `Approved · ${amount}`, id)
              },
            }),
        },
        secondary,
      }
    }

    if (allowed.includes('RESOLVE_HOLD')) {
      secondary.push({
        label: 'Reject',
        tone: 'danger',
        kind: 'reject',
        onClick: () =>
          askReason({
            title: 'Reject from hold',
            description: 'Close this exception: the invoice will not be paid.',
            confirmLabel: 'Reject invoice',
            tone: 'danger',
            run: async (reason) => {
              await run(() => api.rejectHold(id, reason), 'Rejected from the exception queue', id)
            },
          }),
      })
      return {
        primary: {
          label: 'Mark issue resolved',
          tone: 'danger',
          kind: 'resolve',
          disabled: busy,
          onClick: () =>
            askReason({
              title: 'Resolve this hold',
              description: `${invoice.raw.holdReason ?? 'This invoice is waiting for review.'} Explain why it is safe to continue; the reason is saved in the activity trail.`,
              confirmLabel: 'Continue invoice',
              tone: 'danger',
              run: async (reason) => {
                await run(() => api.resume(id, reason), 'Issue resolved · invoice is moving again', id)
              },
            }),
        },
        secondary,
      }
    }

    if (allowed.includes('RELEASE_PAYMENT')) {
      const paymentId = invoice.raw.scheduledPaymentId
      return {
        primary: {
          label: `Release payment (${amount})`,
          tone: 'success',
          kind: 'release',
          disabled: busy || !paymentId,
          onClick: () =>
            paymentId &&
            askReason({
              title: 'Release payment',
              description: `Send ${amount} to ${invoice.supplier}. Add an optional note for the payment record.`,
              confirmLabel: 'Release payment',
              tone: 'dark',
              optional: true,
              run: async (reason) => {
                await run(() => api.releasePayment(paymentId, reason || undefined), `Payment released to ${invoice.supplier}`, id)
              },
            }),
        },
        secondary,
      }
    }

    // Nothing this user can do: explain why, using the backend's own state.
    const waitingOn = users.find((u) => u.id === invoice.raw.waitingOnUserId)?.fullName
    const label =
      status === 'PAID'
        ? 'Paid'
        : status === 'REJECTED'
          ? 'Rejected'
          : status === 'IGNORED'
            ? 'Ignored · not an invoice'
            : status === 'RELEASED'
              ? 'Released · awaiting settlement'
              : status === 'SCHEDULED'
                ? 'Scheduled · another finance user must release'
                : status === 'PENDING_APPROVAL'
                  ? `Waiting on ${waitingOn ?? 'approver'}`
                  : isOnHold(status)
                    ? 'On hold · needs review'
                    : `Processing · ${invoice.pipeline.label}`
    return {
      primary: {
        label,
        tone: status === 'PAID' ? 'success' : 'dark',
        kind: status === 'PAID' ? 'paid' : 'info',
        disabled: status !== 'PAID',
      },
      secondary,
    }
  }

  const openInvoiceAudit = () => {
    if (!selectedInvoice) return
    setAuditView({
      invoiceId: selectedInvoice.id,
      title: 'Activity & audit trail',
      subtitle: `${selectedInvoice.id} · what happened, when, and who did it`,
    })
    setAuditEvents([])
    setAuditVerification(null)
    setAuditLoading(false)
    setMobileSidebarOpen(false)
  }

  const openGlobalAudit = useCallback(
    async (verify = false) => {
      setAuditView({ title: 'All activity', subtitle: 'Every recorded change across all invoices.' })
      setAuditEvents([])
      setAuditVerification(null)
      setAuditLoading(true)
      setMobileSidebarOpen(false)
      try {
        const [events, verification] = await Promise.all([
          api.auditLog(1000),
          verify ? api.verifyAudit() : Promise.resolve(null),
        ])
        setAuditEvents([...events].sort((a, b) => a.id - b.id))
        setAuditVerification(verification)
        if (verification) {
          notify(
            verification.valid
              ? `Integrity check passed · ${verification.checked} events intact`
              : `Integrity issue at event #${verification.brokenAtId}: ${verification.problem ?? 'hash mismatch'}`,
            verification.valid ? 'success' : 'error',
          )
        }
      } catch (error) {
        notify(errorMessage(error), 'error')
      } finally {
        setAuditLoading(false)
      }
    },
    [notify],
  )

  const downloadAudit = () => {
    if (!auditView) return
    api
      .exportAudit(auditView.invoiceId)
      .then(() => notify('Audit log downloaded'))
      .catch((error) => notify(errorMessage(error), 'error'))
  }

  const handleDownload = () => {
    if (!selectedInvoice) return
    api.openDocument(selectedInvoice.id).catch((error) => notify(errorMessage(error), 'error'))
  }

  const handleUpload = async (file: File | null) => {
    if (!file) return
    setBusy(true)
    try {
      const result = await api.upload(file)
      setUploadOpen(false)
      notify(
        result.duplicate
          ? `${file.name} was already received · opened the existing invoice`
          : `${file.name} received · you can follow its progress here`,
      )
      setQuery('')
      setActiveFilter('action')
      await refreshList()
      setSelectedId(result.invoiceId)
      setMobileView('preview')
    } catch (error) {
      notify(errorMessage(error), 'error')
    } finally {
      setBusy(false)
    }
  }

  const adminTools = {
    pollInbox: () =>
      run(api.pollInbox, (result) =>
        `Inbox checked · ${Object.entries(result ?? {})
          .map(([k, v]) => `${v} ${k}`)
          .join(' · ')}`,
      ),
    viewAuditLog: () => openGlobalAudit(),
    verifyChain: () => openGlobalAudit(true),
    exportLog: () => api.exportAudit().catch((error) => notify(errorMessage(error), 'error')),
    clearInvoices: () => {
      if (!window.confirm('Remove all invoices, payments and activity records? Users and vendors are kept.')) return
      run(async () => {
        await api.clearInvoices()
        setDetails({})
        setSelectedId(null)
        closeAudit()
      }, 'All invoices removed')
    },
    resetDemo: () => {
      if (!window.confirm('Clear all invoices and activity records, then reload the sample data?')) return
      run(async () => {
        await api.resetDemo()
        setDetails({})
        setSelectedId(null)
        closeAudit()
      }, 'Demo reset · sample data reloaded')
    },
    testLlm: async () => {
      try {
        const result = await api.testLlm()
        notify(`${result.model}: ${result.reply}`)
      } catch (error) {
        notify(errorMessage(error), 'error')
      }
    },
  }

  const clearFilters = () => {
    setActiveFilter('action')
    setQuery('')
  }

  const selectInvoice = (id: string) => {
    setSelectedId(id)
    setMobileView('preview')
  }

  const backToInbox = () => {
    setMobileView('inbox')
    setSelectedId(null)
  }

  const panelActions = actionsFor(selectedInvoice)

  const auditEventsForDrawer = useMemo(() => {
    if (!auditView) return []
    const source = auditView.invoiceId ? (details[auditView.invoiceId]?.auditEvents ?? []) : auditEvents
    return [...source].sort((a, b) => a.id - b.id)
  }, [auditView, details, auditEvents])

  return (
    <div className="h-full w-full overflow-hidden bg-[radial-gradient(130%_130%_at_0%_0%,#e6f4fd_0%,#edf2f8_48%,#e9eef6_100%)] p-0 lg:p-3 xl:p-4">
      <div className="relative mx-auto flex h-full w-full max-w-[1840px] overflow-hidden bg-white lg:rounded-[26px] lg:border lg:border-white/70 lg:shadow-[0_28px_90px_-30px_rgba(15,23,42,0.32)]">
        <Sidebar
          collapsed={sidebarCollapsed}
          mobileOpen={mobileSidebarOpen}
          activeFilter={activeFilter}
          counts={counts}
          total={items.length}
          attentionCount={counts.holds}
          backendDown={backendDown}
          users={users}
          currentUser={currentUser}
          onUserChange={(id) => {
            setUserId(id)
            const user = users.find((u) => u.id === id)
            if (user) notify(`Now acting as ${user.fullName}`)
          }}
          adminTools={currentUser?.role === 'ADMIN' ? adminTools : null}
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
              loading={loading}
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
              users={users}
              primary={panelActions?.primary ?? null}
              secondary={panelActions?.secondary ?? []}
              onBack={backToInbox}
              onDownload={handleDownload}
              onAudit={openInvoiceAudit}
            />
          </div>
        </main>
      </div>

      <UploadModal open={uploadOpen} busy={busy} onClose={() => setUploadOpen(false)} onSubmit={handleUpload} />

      <ReasonModal
        request={reasonRequest}
        busy={busy}
        onClose={() => setReasonRequest(null)}
        onConfirm={async (reason) => {
          if (!reasonRequest) return
          await reasonRequest.run(reason)
          setReasonRequest(null)
        }}
      />

      <AuditTrailDrawer
        open={Boolean(auditView)}
        events={auditEventsForDrawer}
        users={users}
        title={auditView?.title}
        subtitle={auditView?.subtitle}
        verification={auditVerification}
        loading={auditLoading || Boolean(auditView?.invoiceId && !details[auditView.invoiceId])}
        onClose={closeAudit}
        onExport={downloadAudit}
      />

      {toast && (
        <div
          role="status"
          aria-live="polite"
          style={{ bottom: toastBottom }}
          className={cn(
            'animate-toast pointer-events-none fixed right-5 z-[70] flex max-w-[min(520px,calc(100vw-2.5rem))] select-none items-center gap-2.5 rounded-2xl px-4 py-2.5 text-white shadow-2xl',
            toast.tone === 'error' ? 'bg-rose-700 shadow-rose-900/30' : 'bg-slate-900 shadow-slate-900/30',
          )}
        >
          {toast.tone === 'error' ? (
            <AlertTriangle size={16} className="shrink-0 text-rose-200" />
          ) : (
            <CheckCircle2 size={16} className="shrink-0 text-emerald-400" />
          )}
          <p className="text-[12px] font-semibold leading-snug">{toast.message}</p>
        </div>
      )}
    </div>
  )
}
