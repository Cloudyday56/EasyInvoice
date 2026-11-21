export type InvoiceFlag = 'fraud' | 'critical' | 'overdue'

export type InvoiceStatus =
  | 'Needs review'
  | 'Pending approval'
  | 'Approved'
  | 'Scheduled'
  | 'Paid'
  | 'On hold'

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
  currency: 'CAD' | 'USD'
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
}

export type FilterId = 'action' | 'holds' | 'paid'
