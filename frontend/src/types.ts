import type { CardInfo, InvoiceDetail, InvoiceDto } from './api/client'

export type InvoiceFlag = 'fraud' | 'critical' | 'overdue'

export type InvoiceStatus =
  | 'Processing'
  | 'Needs review'
  | 'Pending approval'
  | 'Approved'
  | 'Scheduled'
  | 'Released'
  | 'Paid'
  | 'On hold'
  | 'Rejected'
  | 'Ignored'

export type RiskTone = 'low' | 'medium' | 'high'

export interface Person {
  name: string
  initials: string
  className: string
}

export interface Attachment {
  name: string
  size: string
  pages: number
}

export interface LineItem {
  description: string
  qty: number
  unitPrice: number
  amount: number
}

export interface PaymentMethod {
  kind: 'card' | 'eft'
  label: string
  detail: string
  locked?: boolean
}

export interface PipelineState {
  node: string
  label: string
  progress: number
}

export interface Invoice {
  id: string
  supplier: string
  supplierEmail: string
  supplierInitials: string
  avatarClass: string
  subject: string
  invoiceNumber: string
  amount: number
  currency: string
  taxLabel: string
  taxAmount: number
  received: string
  receivedAt: number
  requester: Person | null
  tags: string[]
  flags: InvoiceFlag[]
  status: InvoiceStatus
  summary: string
  body: string
  dueDate: string
  invoiceDate: string
  paymentTerms: string
  payment: PaymentMethod
  card: CardInfo | null
  lineItems: LineItem[]
  poNumber: string | null
  province: string
  attachment: Attachment
  pipeline: PipelineState
  risk: {
    label: string
    score: number
    tone: RiskTone
  }
  approvedBy?: string
  /** Raw backend row this view model was built from. */
  raw: InvoiceDto
  /** Full detail, loaded when the invoice is selected. */
  detail?: InvoiceDetail
}

export type FilterId = 'action' | 'holds' | 'paid'
