import type { Invoice } from '../types'

const anna = { name: 'Anna Kowalski', initials: 'AK', className: 'bg-rose-100 text-rose-600' }
const miguel = { name: 'Miguel Santos', initials: 'MS', className: 'bg-indigo-100 text-indigo-600' }
const priya = { name: 'Priya Raman', initials: 'PR', className: 'bg-teal-100 text-teal-600' }
const emilie = { name: 'Émilie Tremblay', initials: 'ÉT', className: 'bg-violet-100 text-violet-600' }

export const invoices: Invoice[] = [
  {
    id: 'techsupply-0392',
    supplier: 'TechSupply Distribution',
    supplierEmail: 'ar@techsupply.com',
    supplierInitials: 'TS',
    avatarClass: 'bg-sky-100 text-sky-600',
    subject: 'Re: Invoice #INV-TS-2026-0392 - Updated shipping address',
    invoiceNumber: 'INV-TS-2026-0392',
    amount: 12500,
    currency: 'CAD',
    taxLabel: 'HST 13%',
    taxAmount: 1438.05,
    invoiceDate: 'Nov 19, 2026',
    payment: { kind: 'card', label: 'Brim card', detail: '•• 4821' },
    lineItems: [
      { description: 'LTL freight — Toronto to Montréal (12 shipments)', qty: 12, unitPrice: 420, amount: 5040 },
      { description: 'Fuel surcharge (18.5%)', qty: 1, unitPrice: 932.4, amount: 932.4 },
      { description: 'Warehouse handling & palletizing', qty: 1, unitPrice: 5089.55, amount: 5089.55 },
    ],
    received: '12 mins ago',
    receivedAt: 12,
    requester: anna,
    tags: ['Invoice', 'Thread'],
    flags: [],
    status: 'Needs review',
    summary:
      'Email thread discussing the invoice and an updated Toronto warehouse address. PO 1042 is open with $8,400 remaining. Vendor, tax and bank details all match the vendor master, and no duplicate invoice number was found. Safe to approve.',
    body: `Hi Anna,

Thanks for confirming! Yes, please use our new warehouse address for all future shipments:

TechSupply Distribution Centre
1847 Industrial Parkway
Toronto, ON M6B 1A9

I've attached the updated invoice for the November freight run under PO 1042. HST at 13% is applied as usual and payment terms remain Net 30.

We'll process Invoice #INV-TS-2026-0392 by November 28 as discussed. Please let me know if you need anything else.

Best regards,
Claire Bennett
Accounts Receivable · TechSupply Distribution`,
    dueDate: 'Dec 19, 2026',
    paymentTerms: 'Net 30',
    poNumber: 'PO-1042',
    province: 'Ontario · HST 13%',
    attachment: { name: 'INV-TS-2026-0392.pdf', size: '284 KB', pages: 2 },
    pipeline: { node: 'N7', label: 'Approval routing', progress: 70 },
    risk: { label: 'Low risk', score: 12, tone: 'low' },
  },
  {
    id: 'acme-1847',
    supplier: 'Acme Freight Parts',
    supplierEmail: 'accounts@acmefreight-parts.co',
    supplierInitials: 'AF',
    avatarClass: 'bg-amber-100 text-amber-700',
    subject: 'URGENT: Invoice #INV-2026-1847 — payment reminder',
    invoiceNumber: 'INV-2026-1847',
    amount: 45850,
    currency: 'CAD',
    taxLabel: 'GST 5%',
    taxAmount: 2183.33,
    invoiceDate: 'Nov 14, 2026',
    payment: { kind: 'eft', label: 'EFT', detail: 'Meridian Trust •• 4471', locked: true },
    lineItems: [
      { description: 'Brake drum assemblies — HD', qty: 20, unitPrice: 1450, amount: 29000 },
      { description: 'Air suspension kits', qty: 10, unitPrice: 1436.67, amount: 14366.7 },
      { description: 'Expedited freight', qty: 1, unitPrice: 299.97, amount: 299.97 },
    ],
    received: '38 mins ago',
    receivedAt: 38,
    requester: null,
    tags: ['Invoice', 'Payment reminder'],
    flags: ['fraud', 'critical'],
    status: 'On hold',
    summary:
      'Remit-to account ending 4471 differs from the vendor master (8820). Bank details were changed 4 days ago and are still unverified. The sender domain acmefreight-parts.co is one character away from acmefreightparts.com. Payment is locked in Fraud hold.',
    body: `URGENT — PAYMENT OVERDUE

Hello,

Our records show invoice INV-2026-1847 for $45,850.00 remains unpaid. Please remit payment immediately to our updated account:

Bank: Meridian Trust
Transit: 00472
Account: 9910 4471

Our previous account is closed. Kindly update your vendor file and confirm by reply.

If payment is not received within 48 hours, we will place your account on credit hold.

Accounts Receivable
Acme Freight Parts`,
    dueDate: 'Nov 24, 2026',
    paymentTerms: 'Net 10',
    poNumber: null,
    province: 'Alberta · GST 5%',
    attachment: { name: 'INV-2026-1847.pdf', size: '612 KB', pages: 3 },
    pipeline: { node: 'N6', label: 'Fraud hold', progress: 50 },
    risk: { label: 'High risk', score: 92, tone: 'high' },
  },
  {
    id: 'cloudhosting-0318',
    supplier: 'CloudHosting Canada',
    supplierEmail: 'billing@cloudhosting.ca',
    supplierInitials: 'CH',
    avatarClass: 'bg-orange-100 text-orange-600',
    subject: 'Monthly invoice #CH-2026-0318 — November hosting',
    invoiceNumber: 'CH-2026-0318',
    amount: 3449.25,
    currency: 'CAD',
    taxLabel: 'GST + QST',
    taxAmount: 449.25,
    invoiceDate: 'Nov 16, 2026',
    payment: { kind: 'card', label: 'Brim card', detail: '•• 4821' },
    lineItems: [
      { description: 'Production hosting — 12 environments', qty: 12, unitPrice: 200, amount: 2400 },
      { description: 'Nightly backup & storage', qty: 1, unitPrice: 350, amount: 350 },
      { description: 'Montréal region egress', qty: 1, unitPrice: 250, amount: 250 },
    ],
    received: '2 hours ago',
    receivedAt: 120,
    requester: anna,
    tags: ['Invoice', 'Recurring'],
    flags: [],
    status: 'Pending approval',
    summary:
      'Recurring monthly hosting invoice for the same amount as October. GST and QST match the Québec rate for a Montréal billing address. The contract is active and no flags were raised.',
    body: `Hello Anna,

Your November hosting invoice is ready. The plan covers 12 production environments, nightly backups and the Montréal region egress allowance.

Subtotal: $3,000.00
GST (5%): $150.00
QST (9.975%): $299.25
Total: $3,449.25 CAD

Payment is due December 1 under Net 15 terms. This is the same amount as October — no plan changes this month.

Thank you,
Billing Team
CloudHosting Canada`,
    dueDate: 'Dec 1, 2026',
    paymentTerms: 'Net 15',
    poNumber: null,
    province: 'Québec · GST + QST',
    attachment: { name: 'CH-2026-0318.pdf', size: '196 KB', pages: 2 },
    pipeline: { node: 'N8', label: 'Approval execution', progress: 80 },
    risk: { label: 'Low risk', score: 6, tone: 'low' },
  },
  {
    id: 'buildright-8841',
    supplier: 'BuildRight Construction',
    supplierEmail: 'invoices@buildright.ca',
    supplierInitials: 'BR',
    avatarClass: 'bg-emerald-100 text-emerald-600',
    subject: 'Invoice #INV-8841 — Warehouse bay door replacement',
    invoiceNumber: 'INV-8841',
    amount: 8240,
    currency: 'CAD',
    taxLabel: 'HST 13%',
    taxAmount: 947.96,
    invoiceDate: 'Nov 18, 2026',
    payment: { kind: 'eft', label: 'EFT', detail: 'CIBC •• 0193' },
    lineItems: [
      { description: 'Remove existing bay doors', qty: 2, unitPrice: 1850, amount: 3700 },
      { description: 'High-speed door install', qty: 2, unitPrice: 1450, amount: 2900 },
      { description: 'Disposal & site cleanup', qty: 1, unitPrice: 692.04, amount: 692.04 },
    ],
    received: '5 hours ago',
    receivedAt: 300,
    requester: miguel,
    tags: ['Invoice'],
    flags: [],
    status: 'Approved',
    summary:
      'Approved by Daniel Okafor on Nov 20. HST matches Ontario, PO 1099 is fully consumed, and the vendor has been paid 11 times without incident. Payment is scheduled for Dec 18 by EFT.',
    body: `Hi Miguel,

Please find attached our invoice for the warehouse bay door replacement at the Toronto facility. Work was completed and signed off on November 18.

Subtotal: $7,292.04
HST (13%): $947.96
Total: $8,240.00 CAD

Payment terms are Net 30, due December 18. Thank you for the business.

Daniel Rousseau
BuildRight Construction`,
    dueDate: 'Dec 18, 2026',
    paymentTerms: 'Net 30',
    poNumber: 'PO-1099',
    province: 'Ontario · HST 13%',
    attachment: { name: 'INV-8841.pdf', size: '348 KB', pages: 2 },
    pipeline: { node: 'N9', label: 'Payment scheduling', progress: 90 },
    risk: { label: 'Low risk', score: 9, tone: 'low' },
    approvedBy: 'Daniel Okafor',
  },
  {
    id: 'northwind-55120',
    supplier: 'Northwind Couriers',
    supplierEmail: 'billing@northwindcouriers.ca',
    supplierInitials: 'NC',
    avatarClass: 'bg-indigo-100 text-indigo-600',
    subject: 'Invoice NW-55120 — GTA courier runs, Nov 12–18',
    invoiceNumber: 'NW-55120',
    amount: 1890.75,
    currency: 'CAD',
    taxLabel: 'GST + PST',
    taxAmount: 202.58,
    invoiceDate: 'Oct 15, 2026',
    payment: { kind: 'eft', label: 'EFT', detail: 'TD •• 7742' },
    lineItems: [
      { description: 'GTA courier runs — Nov 12–18', qty: 1, unitPrice: 1288.17, amount: 1288.17 },
      { description: 'After-hours deliveries', qty: 4, unitPrice: 75, amount: 300 },
      { description: 'Fuel surcharge', qty: 1, unitPrice: 100, amount: 100 },
    ],
    received: '1 day ago',
    receivedAt: 1440,
    requester: priya,
    tags: ['Invoice', 'Overdue'],
    flags: ['overdue'],
    status: 'Pending approval',
    summary:
      'Invoice is 7 days past due. GST and PST match the British Columbia rate, but the invoice is $240 above the courier contract cap. Approval was escalated to an AP Admin.',
    body: `Hi Priya,

Attached is invoice NW-55120 for the November 12–18 courier runs across the GTA. This invoice was due on November 14.

Subtotal: $1,688.17
GST (5%): $84.41
PST (7%): $118.17
Total: $1,890.75 CAD

Could you confirm when payment will be released? Our driver can pick up a cheque if that is easier.

Thanks,
Northwind Couriers`,
    dueDate: 'Nov 14, 2026',
    paymentTerms: 'Net 30',
    poNumber: null,
    province: 'British Columbia · GST + PST',
    attachment: { name: 'NW-55120.pdf', size: '142 KB', pages: 1 },
    pipeline: { node: 'N8', label: 'Approval execution', progress: 80 },
    risk: { label: 'Medium risk', score: 46, tone: 'medium' },
  },
  {
    id: 'mapleleaf-2291',
    supplier: 'Maple Leaf Office Supply',
    supplierEmail: 'orders@mapleleafoffice.ca',
    supplierInitials: 'ML',
    avatarClass: 'bg-lime-100 text-lime-700',
    subject: 'Invoice ML-2291 — November office restock',
    invoiceNumber: 'ML-2291',
    amount: 642.3,
    currency: 'CAD',
    taxLabel: 'HST 13%',
    taxAmount: 73.89,
    invoiceDate: 'Oct 20, 2026',
    payment: { kind: 'card', label: 'Brim card', detail: '•• 4821' },
    lineItems: [
      { description: 'Printer paper — cases', qty: 12, unitPrice: 28.5, amount: 342 },
      { description: 'Toner cartridges', qty: 4, unitPrice: 42.75, amount: 171 },
      { description: 'Shipping labels — packs', qty: 6, unitPrice: 9.235, amount: 55.41 },
    ],
    received: '1 day ago',
    receivedAt: 1560,
    requester: anna,
    tags: ['Invoice', 'Recurring'],
    flags: [],
    status: 'Paid',
    summary:
      'Paid by EFT on Nov 19 and matched to the bank settlement. HST is included in the total and the receipt is attached for audit.',
    body: `Hi Anna,

Thanks for your order! Invoice ML-2291 covers the November restock of printer paper, toner and shipping labels for the Toronto office.

Total: $642.30 CAD (HST included)

This invoice was paid by EFT on November 19 — no action needed. Receipt attached for your records.

Maple Leaf Office Supply`,
    dueDate: 'Paid Nov 19, 2026',
    paymentTerms: 'Net 30',
    poNumber: null,
    province: 'Ontario · HST 13%',
    attachment: { name: 'ML-2291.pdf', size: '98 KB', pages: 1 },
    pipeline: { node: 'N10', label: 'Payment settled', progress: 100 },
    risk: { label: 'Low risk', score: 3, tone: 'low' },
  },
  {
    id: 'pacificrim-7714',
    supplier: 'Pacific Rim Freight',
    supplierEmail: 'ar@pacificrimfreight.com',
    supplierInitials: 'PR',
    avatarClass: 'bg-violet-100 text-violet-600',
    subject: 'Invoice PRF-7714 — Vancouver–Shanghai ocean freight',
    invoiceNumber: 'PRF-7714',
    amount: 18300,
    currency: 'USD',
    taxLabel: 'Zero-rated',
    taxAmount: 0,
    invoiceDate: 'Nov 12, 2026',
    payment: { kind: 'eft', label: 'EFT', detail: 'HSBC •• 2210' },
    lineItems: [
      { description: 'Ocean freight — Vancouver to Shanghai', qty: 2, unitPrice: 7500, amount: 15000 },
      { description: 'Bunker adjustment factor', qty: 1, unitPrice: 2100, amount: 2100 },
      { description: 'Documentation & customs', qty: 1, unitPrice: 1200, amount: 1200 },
    ],
    received: '2 days ago',
    receivedAt: 2880,
    requester: null,
    tags: ['Invoice', 'FX'],
    flags: ['critical'],
    status: 'Needs review',
    summary:
      'USD invoice converted at the Bank of Canada rate for Nov 19 (1.3712). The vendor is matched by name, but the invoice is missing a GST number and the remit-to entity is in the US. Needs a human review of reverse-charge tax treatment.',
    body: `Hello,

Please find invoice PRF-7714 for ocean freight services on the Vancouver–Shanghai lane, October sailings.

Amount: USD 18,300.00
Payment terms: Net 30, due December 12.

Our GST registration number is not printed on this invoice because the service is billed from our US entity. Please confirm the correct reverse-charge treatment for your records.

Pacific Rim Freight`,
    dueDate: 'Dec 12, 2026',
    paymentTerms: 'Net 30',
    poNumber: 'PO-2087',
    province: 'British Columbia · Zero-rated',
    attachment: { name: 'PRF-7714.pdf', size: '224 KB', pages: 2 },
    pipeline: { node: 'N4', label: 'Vendor matching', progress: 40 },
    risk: { label: 'Medium risk', score: 58, tone: 'medium' },
  },
  {
    id: 'quebecsignal-009',
    supplier: 'Québec Signalisation',
    supplierEmail: 'facturation@quebecsignalisation.ca',
    supplierInitials: 'QS',
    avatarClass: 'bg-teal-100 text-teal-600',
    subject: 'Facture QS-2026-009 — Signalisation du stationnement',
    invoiceNumber: 'QS-2026-009',
    amount: 2414.48,
    currency: 'CAD',
    taxLabel: 'GST + QST',
    taxAmount: 314.48,
    invoiceDate: 'Nov 2, 2026',
    payment: { kind: 'card', label: 'Brim card', detail: '•• 4821' },
    lineItems: [
      { description: 'Panneaux de signalisation', qty: 14, unitPrice: 125, amount: 1750 },
      { description: 'Installation', qty: 1, unitPrice: 250, amount: 250 },
      { description: 'Livraison', qty: 1, unitPrice: 100, amount: 100 },
    ],
    received: '3 days ago',
    receivedAt: 4320,
    requester: emilie,
    tags: ['Facture', 'FR'],
    flags: [],
    status: 'Pending approval',
    summary:
      'French-language invoice from a matched Québec vendor. GST and QST are correct for the Montréal ship-to address. Waiting on the second approver, Marc Gagnon.',
    body: `Bonjour Émilie,

Veuillez trouver ci-joint la facture QS-2026-009 pour la signalisation du stationnement de Montréal.

Sous-total : 2 100,00 $
TPS (5 %) : 105,00 $
TVQ (9,975 %) : 209,48 $
Total : 2 414,48 $ CA

Modalités : Net 30, échéance le 2 décembre 2026.

Merci,
Québec Signalisation`,
    dueDate: 'Dec 2, 2026',
    paymentTerms: 'Net 30',
    poNumber: 'PO-3110',
    province: 'Québec · GST + QST',
    attachment: { name: 'QS-2026-009.pdf', size: '176 KB', pages: 1 },
    pipeline: { node: 'N8', label: 'Approval execution', progress: 80 },
    risk: { label: 'Low risk', score: 11, tone: 'low' },
  },
  {
    id: 'lakeshore-9032',
    supplier: 'Lakeshore Fuel Depot',
    supplierEmail: 'billing@lakeshorefuel.ca',
    supplierInitials: 'LF',
    avatarClass: 'bg-amber-100 text-amber-700',
    subject: 'Invoice LF-9032 — October diesel purchases',
    invoiceNumber: 'LF-9032',
    amount: 6780.45,
    currency: 'CAD',
    taxLabel: 'HST 13%',
    taxAmount: 322.88,
    invoiceDate: 'Oct 26, 2026',
    payment: { kind: 'eft', label: 'EFT', detail: 'RBC •• 5581' },
    lineItems: [
      { description: 'Diesel — cardlock October', qty: 1, unitPrice: 5890.57, amount: 5890.57 },
      { description: 'DEF fluid', qty: 1, unitPrice: 567, amount: 567 },
      { description: 'Equipment wash', qty: 1, unitPrice: 55, amount: 55 },
    ],
    received: '4 days ago',
    receivedAt: 5760,
    requester: null,
    tags: ['Invoice', 'Past due'],
    flags: ['overdue', 'critical'],
    status: 'On hold',
    summary:
      'Line items sum to $6,512.57 but the printed subtotal is $6,457.57 — a $55.00 discrepancy. Arithmetic validation is blocked at N5 until the supplier issues a corrected invoice. The invoice is also past due.',
    body: `Hi,

Attached is invoice LF-9032 for diesel purchases at the Lakeshore cardlock during October.

Subtotal: $6,457.57
GST (5%): $322.88
Total: $6,780.45 CAD

This invoice is now past due. Please advise on payment status.

Lakeshore Fuel Depot`,
    dueDate: 'Nov 10, 2026',
    paymentTerms: 'Net 15',
    poNumber: null,
    province: 'Ontario · HST 13%',
    attachment: { name: 'LF-9032.pdf', size: '310 KB', pages: 2 },
    pipeline: { node: 'N5', label: 'Validation failed', progress: 45 },
    risk: { label: 'Medium risk', score: 44, tone: 'medium' },
  },
]
