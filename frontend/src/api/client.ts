// Thin typed client for the Spring Boot API (see backend/.../web).
// Every request carries the demo "login" in the X-User-Id header.
// In dev, Vite proxies /api to :8080; in Docker, nginx proxies /api to the backend service.

export type BackendInvoiceStatus =
  | 'RECEIVED'
  | 'IGNORED'
  | 'EXTRACTED'
  | 'VALIDATING'
  | 'ON_HOLD'
  | 'ON_HOLD_VENDOR'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SCHEDULED'
  | 'RELEASED'
  | 'PAID'
  | 'REJECTED'

export type Role = 'EMPLOYEE' | 'APPROVER' | 'ADMIN'
export type Action = 'APPROVE_INVOICE' | 'RESOLVE_HOLD' | 'RELEASE_PAYMENT'
export type PaymentMethodCode = 'EFT' | 'CHEQUE' | 'CARD'
export type PaymentStatus = 'SCHEDULED' | 'RELEASED' | 'SENT' | 'SETTLED' | 'FAILED'

export interface UserDto {
  id: string
  fullName: string
  email: string
  title: string | null
  departmentId: string | null
  managerId: string | null
  role: Role
  sourceRole: string | null
  approvalLimitCad: number | null
  status: string
  delegateUserId: string | null
  active: boolean
  onLeave: boolean
}

export interface InvoiceDto {
  id: string
  sourceDocumentId: number | null
  documentType: string
  vendorId: string | null
  vendorNameRaw: string | null
  vendorInvoiceNumber: string | null
  canonicalInvoiceNumber: string | null
  invoiceDate: string | null
  receivedDate: string | null
  dueDate: string | null
  currency: string | null
  subtotal: number | null
  gstHstAmount: number | null
  pstQstAmount: number | null
  total: number | null
  fxRateToCad: number | null
  totalCad: number | null
  departmentId: string | null
  requesterUserId: string | null
  poNumberRaw: string | null
  poId: string | null
  contractId: string | null
  shipToProvince: string | null
  riskScore: number | null
  status: BackendInvoiceStatus
  sourceStatus: string | null
  holdQueue: string | null
  holdReason: string | null
  resumeNode: string | null
  currentNode: string | null
  paymentMethod: string | null
  paymentReference: string | null
  flags: FlagDto[]
  allowedActions: Action[]
  waitingOnUserId: string | null
  scheduledPaymentId: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface InvoiceLineDto {
  id: number
  lineNo: number
  description: string | null
  quantity: number | null
  unitPrice: number | null
  amount: number | null
  glAccount: string | null
}

export interface FlagDto {
  id: number
  node: string
  code: string
  severity: 'FLAG' | 'BLOCK'
  message: string
  createdAt: string
  overriddenBy: string | null
  overrideReason: string | null
}

export interface ApprovalStepDto {
  id: number
  stepNo: number
  requiredRole: Role
  reason: string | null
  assignedUserId: string | null
  actingFor: string | null
  status: string
  actedBy: string | null
  actedAt: string | null
  comment: string | null
  pending: boolean
  approved: boolean
}

export interface PaymentDto {
  id: string
  invoiceId: string
  vendorId: string | null
  method: PaymentMethodCode | null
  scheduledDate: string | null
  paidDate: string | null
  currency: string | null
  amount: number | null
  reference: string | null
  status: PaymentStatus
  releasedBy: string | null
  failureReason: string | null
}

export interface AuditEventDto {
  id: number
  invoiceId: string | null
  actorId: string | null
  actingFor: string | null
  role: string | null
  action: string
  node: string | null
  fromStatus: string | null
  toStatus: string | null
  payloadJson: string | null
  createdAt: string
  hash: string
}

export interface NodeProgress {
  node: string
  label: string
  state: 'pending' | 'running' | 'done' | 'flagged' | 'blocked' | 'waiting' | 'retrying' | 'ignored'
  message: string | null
}

export interface DocumentInfo {
  id: number
  channel: 'UPLOAD' | 'EMAIL'
  fileName: string | null
  contentType: string | null
  sender: string | null
  subject: string | null
  body: string | null
}

export interface InvoiceDetail {
  invoice: InvoiceDto
  lines: InvoiceLineDto[]
  flags: FlagDto[]
  approvalSteps: ApprovalStepDto[]
  payments: PaymentDto[]
  auditEvents: AuditEventDto[]
  document: DocumentInfo | null
  pipeline: NodeProgress[]
  allowedActions: Action[]
}

export interface VendorOption {
  id: string
  legalName: string
  category: string | null
  status: string
}

export interface DepartmentDto {
  id: string
  name: string
  costCentre: string | null
  managerUserId: string | null
}

export interface Lookups {
  vendors: VendorOption[]
  departments: DepartmentDto[]
}

export interface IntakeResult {
  invoiceId: string
  duplicate: boolean
}

export interface VerifyResult {
  valid: boolean
  checked: number
  brokenAtId: number | null
  problem: string | null
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

const USER_KEY = 'easyinvoice.userId'

export function getStoredUserId(): string | null {
  try {
    return window.localStorage.getItem(USER_KEY)
  } catch {
    return null
  }
}

export function storeUserId(id: string) {
  try {
    window.localStorage.setItem(USER_KEY, id)
  } catch {
    /* ignore */
  }
}

let currentUserId: string | null = getStoredUserId()

export function setCurrentUserId(id: string | null) {
  currentUserId = id
  if (id) storeUserId(id)
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (currentUserId) headers.set('X-User-Id', currentUserId)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`/api${path}`, { ...init, headers })
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`
    try {
      const body = await response.json()
      if (body?.error) message = body.error
    } catch {
      /* not JSON */
    }
    throw new ApiError(response.status, message)
  }
  return response
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init)
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

const post = <T = void>(path: string, body?: unknown) =>
  json<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

async function downloadBlob(path: string, fallbackName: string) {
  const response = await request(path)
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^"]+)"?/.exec(disposition)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = match?.[1] ?? fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

async function openBlob(path: string) {
  const response = await request(path)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener')
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export const api = {
  users: () => json<UserDto[]>('/users'),
  me: () => json<UserDto>('/me'),
  lookups: () => json<Lookups>('/lookups'),

  invoices: () => json<InvoiceDto[]>('/invoices'),
  invoice: (id: string) => json<InvoiceDetail>(`/invoices/${encodeURIComponent(id)}`),
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return json<IntakeResult>('/invoices/upload', { method: 'POST', body: form })
  },
  approve: (id: string, reason?: string) => post(`/invoices/${encodeURIComponent(id)}/approve`, { reason }),
  reject: (id: string, reason: string) => post(`/invoices/${encodeURIComponent(id)}/reject`, { reason }),
  returnToAp: (id: string, reason: string) => post(`/invoices/${encodeURIComponent(id)}/return`, { reason }),
  resume: (id: string, reason: string) => post(`/invoices/${encodeURIComponent(id)}/resume`, { reason }),
  rejectHold: (id: string, reason: string) =>
    post(`/invoices/${encodeURIComponent(id)}/reject-hold`, { reason }),
  openDocument: (id: string) => openBlob(`/invoices/${encodeURIComponent(id)}/document`),

  releasePayment: (paymentId: string) => post(`/payments/${encodeURIComponent(paymentId)}/release`),

  exportAudit: (invoiceId?: string) =>
    downloadBlob(
      invoiceId ? `/audit/export.csv?invoiceId=${encodeURIComponent(invoiceId)}` : '/audit/export.csv',
      invoiceId ? `audit-${invoiceId}.csv` : 'audit-log.csv',
    ),
  verifyAudit: () => json<VerifyResult>('/audit/verify'),

  pollInbox: () => post<Record<string, number>>('/admin/poll-inbox'),
  clearInvoices: () => post<{ status: string }>('/admin/clear-invoices'),
  resetDemo: () => post<{ status: string }>('/admin/reset-demo'),
  testLlm: () => post<{ model: string; reply: string }>('/admin/llm/test'),
}
