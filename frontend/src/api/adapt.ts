// Maps backend DTOs onto the view model the UI components were designed around.
import type { Invoice, InvoiceFlag, InvoiceStatus, PaymentMethod, Person, RiskTone } from '../types'
import type { BackendInvoiceStatus, InvoiceDetail, InvoiceDto, Lookups, UserDto } from './client'
import { formatDate, initials, timeAgo } from '../lib/format'

const NODE_ORDER = [
  'INTAKE_GATEWAY',
  'EMAIL_CLASSIFIER',
  'EXTRACTION',
  'VENDOR_MATCH',
  'VALIDATION_RULES',
  'FRAUD_SCORING',
  'APPROVAL_ROUTING',
  'APPROVAL_EXECUTION',
  'PAYMENT_SCHEDULING',
  'PAYMENT_RELEASE',
]

const NODE_LABELS: Record<string, string> = {
  INTAKE_GATEWAY: 'Intake gateway',
  EMAIL_CLASSIFIER: 'Email classifier',
  EXTRACTION: 'Extraction',
  VENDOR_MATCH: 'Vendor match',
  VALIDATION_RULES: 'Validation rules',
  FRAUD_SCORING: 'Fraud & risk scoring',
  APPROVAL_ROUTING: 'Approval routing',
  APPROVAL_EXECUTION: 'Approval execution',
  PAYMENT_SCHEDULING: 'Payment scheduling',
  PAYMENT_RELEASE: 'Payment release',
}

const HOLD_LABELS: Record<string, string> = {
  INTAKE_ERRORS: 'Intake errors',
  MISSING_DOCUMENT: 'Missing document',
  EXTRACTION_REVIEW: 'Extraction review',
  VENDOR_HOLD: 'Vendor hold',
  VALIDATION_FAILED: 'Validation failed',
  FRAUD_HOLD: 'Fraud hold',
  ROUTING_ERROR: 'Routing error',
  PAYMENT_EXCEPTIONS: 'Payment exceptions',
}

const AVATAR_CLASSES = [
  'bg-sky-100 text-sky-600',
  'bg-amber-100 text-amber-700',
  'bg-emerald-100 text-emerald-700',
  'bg-violet-100 text-violet-600',
  'bg-rose-100 text-rose-600',
  'bg-indigo-100 text-indigo-600',
  'bg-teal-100 text-teal-600',
  'bg-orange-100 text-orange-600',
]

function avatarClass(key: string) {
  let hash = 0
  for (const ch of key) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return AVATAR_CLASSES[Math.abs(hash) % AVATAR_CLASSES.length]
}

export function toPerson(user: UserDto): Person {
  return { name: user.fullName, initials: initials(user.fullName), className: avatarClass(user.id) }
}

export function roleLabel(user: UserDto) {
  const role = user.role === 'ADMIN' ? 'Admin' : user.role === 'APPROVER' ? 'Approver' : 'Employee'
  return [user.title, role, user.onLeave ? 'on leave' : null]
    .filter(Boolean)
    .join(' · ')
}

const STATUS_MAP: Record<BackendInvoiceStatus, InvoiceStatus> = {
  RECEIVED: 'Processing',
  EXTRACTED: 'Processing',
  VALIDATING: 'Processing',
  ON_HOLD: 'On hold',
  ON_HOLD_VENDOR: 'On hold',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  SCHEDULED: 'Scheduled',
  RELEASED: 'Released',
  PAID: 'Paid',
  REJECTED: 'Rejected',
  IGNORED: 'Ignored',
}

const TERMINAL: BackendInvoiceStatus[] = ['PAID', 'REJECTED', 'IGNORED']

export const isTerminal = (status: BackendInvoiceStatus) => TERMINAL.includes(status)
export const isOnHold = (status: BackendInvoiceStatus) => status === 'ON_HOLD' || status === 'ON_HOLD_VENDOR'

function riskOf(score: number | null): Invoice['risk'] {
  if (score == null) return { label: 'Not scored', score: 0, tone: 'low' }
  const tone: RiskTone = score >= 60 ? 'high' : score >= 30 ? 'medium' : 'low'
  const label = tone === 'high' ? 'High risk' : tone === 'medium' ? 'Medium risk' : 'Low risk'
  return { label, score, tone }
}

function taxLabel(province: string | null, pst: number) {
  const p = (province ?? '').toUpperCase()
  if (p === 'ON') return 'HST 13%'
  if (['NS', 'NB', 'NL', 'PE'].includes(p)) return 'HST 15%'
  if (p === 'QC') return 'GST + QST'
  if (['BC', 'MB', 'SK'].includes(p)) return pst > 0 ? 'GST + PST' : 'GST 5%'
  if (p) return 'GST 5%'
  return 'Sales tax'
}

function pipelineState(dto: InvoiceDto): Invoice['pipeline'] {
  if (dto.status === 'PAID') return { node: 'N10', label: 'Payment settled', progress: 100 }
  const index = dto.currentNode ? NODE_ORDER.indexOf(dto.currentNode) : -1
  if (index < 0) return { node: '—', label: dto.status.replace(/_/g, ' ').toLowerCase(), progress: 0 }
  return {
    node: `N${index + 1}`,
    label: NODE_LABELS[dto.currentNode!] ?? dto.currentNode!,
    progress: Math.round((index / NODE_ORDER.length) * 100),
  }
}

function paymentOf(dto: InvoiceDto, detail?: InvoiceDetail): PaymentMethod {
  const payment = detail?.payments[detail.payments.length - 1]
  const locked = dto.holdQueue === 'FRAUD_HOLD'
  if (payment?.method === 'CARD') {
    return { kind: 'card', label: 'Brim card', detail: payment.reference ?? 'virtual card', locked }
  }
  if (payment?.method === 'CHEQUE') {
    return { kind: 'eft', label: 'Cheque', detail: payment.reference ?? '', locked }
  }
  return { kind: 'eft', label: 'EFT', detail: payment?.reference ?? 'vendor account on file', locked }
}

function flagsOf(dto: InvoiceDto, detail?: InvoiceDetail): InvoiceFlag[] {
  const flags = new Set<InvoiceFlag>()
  if (dto.holdQueue === 'FRAUD_HOLD' || (dto.riskScore ?? 0) >= 40) flags.add('fraud')
  if (isOnHold(dto.status) && dto.holdQueue !== 'FRAUD_HOLD') flags.add('critical')
  if (detail?.flags.some((f) => f.severity === 'BLOCK' && !f.overriddenBy)) flags.add('critical')
  if (detail?.flags.some((f) => /FRAUD|BANK/i.test(f.code) && !f.overriddenBy)) flags.add('fraud')
  if (dto.dueDate && !isTerminal(dto.status)) {
    const [y, m, d] = dto.dueDate.split('-').map(Number)
    if (new Date(y, m - 1, d).getTime() < Date.now()) flags.add('overdue')
  }
  return [...flags]
}

function summaryOf(dto: InvoiceDto, detail?: InvoiceDetail): string {
  const parts: string[] = []
  if (dto.holdQueue) {
    parts.push(`Parked in ${HOLD_LABELS[dto.holdQueue] ?? dto.holdQueue}${dto.holdReason ? `: ${dto.holdReason}` : '.'}`)
  }
  const open = detail?.flags.filter((f) => !f.overriddenBy) ?? []
  for (const f of open.slice(0, 4)) parts.push(`${f.severity === 'BLOCK' ? 'Blocked' : 'Flag'} (${f.code}): ${f.message}`)
  const overridden = detail?.flags.filter((f) => f.overriddenBy) ?? []
  if (overridden.length) parts.push(`${overridden.length} flag(s) overridden by an Admin.`)
  const pending = detail?.approvalSteps.find((s) => s.pending)
  if (dto.status === 'PENDING_APPROVAL' && pending) {
    parts.push(
      `Waiting on approval step ${pending.stepNo} (${pending.assignedUserId ?? 'unassigned'}${
        pending.actingFor ? `, acting for ${pending.actingFor}` : ''
      }).`,
    )
  }
  if (parts.length === 0) {
    if (!detail) return 'Loading pipeline details…'
    if (dto.status === 'PAID') return 'Settled. All checks passed and the payment is confirmed by the bank.'
    if (isTerminal(dto.status)) return `Invoice is ${dto.status.toLowerCase()}.`
    return 'No flags raised so far. Vendor, tax and duplicate checks have not found any issues.'
  }
  return parts.join(' ')
}

export function toInvoice(
  dto: InvoiceDto,
  ctx: { lookups: Lookups | null; users: UserDto[] },
  detail?: InvoiceDetail,
  now = Date.now(),
): Invoice {
  const vendor = ctx.lookups?.vendors.find((v) => v.id === dto.vendorId)
  const supplier = vendor?.legalName ?? dto.vendorNameRaw ?? detail?.document?.sender ?? 'Unknown vendor'
  const requesterUser = ctx.users.find((u) => u.id === dto.requesterUserId)
  const doc = detail?.document
  const currency = dto.currency ?? 'CAD'
  const tax = (dto.gstHstAmount ?? 0) + (dto.pstQstAmount ?? 0)
  const invoiceNumber = dto.vendorInvoiceNumber ?? dto.canonicalInvoiceNumber ?? dto.id
  const createdMs = dto.createdAt ? new Date(dto.createdAt).getTime() : now
  const approved = detail?.approvalSteps.filter((s) => s.approved && s.actedBy).map((s) => s.actedBy!)
  const tags = [
    dto.documentType?.replace(/_/g, ' ').toLowerCase(),
    doc?.channel === 'EMAIL' ? 'Email' : doc?.channel === 'UPLOAD' ? 'Upload' : null,
    dto.holdQueue ? HOLD_LABELS[dto.holdQueue] : null,
    ...(detail?.flags.map((f) => f.code) ?? []),
  ].filter((t): t is string => Boolean(t))

  return {
    id: dto.id,
    supplier,
    supplierEmail: doc?.sender ?? '',
    supplierInitials: initials(supplier),
    avatarClass: avatarClass(dto.vendorId ?? supplier),
    subject: doc?.subject ?? doc?.fileName ?? `Invoice ${invoiceNumber}`,
    invoiceNumber,
    amount: dto.total ?? 0,
    currency,
    taxLabel: taxLabel(dto.shipToProvince, dto.pstQstAmount ?? 0),
    taxAmount: tax,
    received: timeAgo(dto.createdAt, now),
    receivedAt: Math.max(0, (now - createdMs) / 60_000),
    requester: requesterUser ? toPerson(requesterUser) : null,
    tags: [...new Set(tags)],
    flags: flagsOf(dto, detail),
    status: STATUS_MAP[dto.status] ?? 'Processing',
    summary: summaryOf(dto, detail),
    body: doc?.body ?? (doc?.channel === 'UPLOAD' ? 'Uploaded directly through the AP portal.' : 'No email body.'),
    dueDate: formatDate(dto.dueDate),
    invoiceDate: formatDate(dto.invoiceDate),
    paymentTerms: dto.invoiceDate && dto.dueDate ? termsOf(dto.invoiceDate, dto.dueDate) : '—',
    payment: paymentOf(dto, detail),
    lineItems:
      detail?.lines.map((l) => ({
        description: l.description ?? `Line ${l.lineNo}`,
        qty: l.quantity ?? 1,
        unitPrice: l.unitPrice ?? l.amount ?? 0,
        amount: l.amount ?? 0,
      })) ?? [],
    poNumber: dto.poNumberRaw ?? dto.poId,
    province: dto.shipToProvince ?? '',
    attachment: { name: doc?.fileName ?? 'no attachment', size: doc?.contentType ?? '', pages: 1 },
    pipeline: pipelineState(dto),
    risk: riskOf(dto.riskScore),
    approvedBy: approved?.length ? approved.join(', ') : undefined,
    raw: dto,
    detail,
  }
}

function termsOf(invoiceDate: string, dueDate: string) {
  const days = Math.round((Date.parse(dueDate) - Date.parse(invoiceDate)) / 86_400_000)
  if (!Number.isFinite(days)) return '—'
  return days <= 0 ? 'Due on receipt' : `Net ${days}`
}
