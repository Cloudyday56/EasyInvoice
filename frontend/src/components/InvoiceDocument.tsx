import { CreditCard, Landmark, ShieldAlert } from 'lucide-react'
import type { Invoice } from '../types'
import { formatMoney } from '../lib/format'
import { Avatar } from './ui'

const billTo = {
  name: 'Harborline Logistics Inc.',
  address: '120 Bremner Blvd, Suite 1800',
  city: 'Toronto, ON M5J 0A8',
}

function shipTo(invoice: Invoice) {
  if (invoice.province.startsWith('Alberta')) return 'Calgary, AB'
  if (invoice.province.startsWith('British Columbia')) return 'Vancouver, BC'
  if (invoice.province.startsWith('Québec')) return 'Montréal, QC'
  return 'Toronto, ON'
}

export function InvoiceDocument({ invoice }: { invoice: Invoice }) {
  const subtotal = invoice.amount - invoice.taxAmount
  const isCard = invoice.payment.kind === 'card'
  const PaymentIcon = invoice.payment.locked ? ShieldAlert : isCard ? CreditCard : Landmark

  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-100/70 p-3 sm:p-5">
      <div className="relative mx-auto max-w-[680px] overflow-hidden rounded-xl bg-white p-5 shadow-[0_10px_36px_-22px_rgba(15,23,42,0.35)] ring-1 ring-slate-200/70 sm:p-7">
        {(invoice.status === 'Paid' || invoice.status === 'On hold') && (
          <div
            className={
              invoice.status === 'Paid'
                ? 'pointer-events-none absolute right-4 top-24 -rotate-12 rounded-md border-2 border-emerald-500/30 px-3 py-1 text-[13px] font-extrabold uppercase tracking-[0.22em] text-emerald-500/35 sm:right-8 sm:top-28 sm:text-[16px]'
                : 'pointer-events-none absolute right-4 top-24 -rotate-12 rounded-md border-2 border-rose-500/25 px-3 py-1 text-[13px] font-extrabold uppercase tracking-[0.22em] text-rose-500/30 sm:right-8 sm:top-28 sm:text-[16px]'
            }
          >
            {invoice.status === 'Paid' ? 'Paid' : 'Hold'}
          </div>
        )}

        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <Avatar initials={invoice.supplierInitials} className={invoice.avatarClass} size="md" />
            <div className="min-w-0">
              <p className="truncate text-[12px] font-extrabold text-slate-800">{invoice.supplier}</p>
              <p className="mt-0.5 truncate text-[9px] font-medium text-slate-400">
                {invoice.province.startsWith('Québec')
                  ? 'GST/QST No. 8234 5678 90 TQ0001'
                  : 'GST/HST No. 81234 5678 RT0001'}
              </p>
            </div>
          </div>
          <div className="shrink-0 text-right">
            <p className="text-[8.5px] font-extrabold uppercase tracking-[0.2em] text-slate-400">
              Invoice
            </p>
            <p className="mt-0.5 text-[15px] font-extrabold tracking-[-0.01em] text-slate-900 sm:text-[17px]">
              {invoice.invoiceNumber}
            </p>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-slate-100 pt-4 sm:grid-cols-4">
          <DocMeta label="Invoice date" value={invoice.invoiceDate} />
          <DocMeta label="Due date" value={invoice.dueDate} />
          <DocMeta label="Terms" value={invoice.paymentTerms} />
          <DocMeta label="PO number" value={invoice.poNumber ?? '—'} />
        </div>

        <div className="mt-4 flex flex-wrap justify-between gap-4 border-t border-slate-100 pt-4">
          <div>
            <p className="text-[8.5px] font-extrabold uppercase tracking-[0.16em] text-slate-400">
              Bill to
            </p>
            <p className="mt-1 text-[10.5px] font-bold text-slate-700">{billTo.name}</p>
            <p className="mt-0.5 text-[9.5px] leading-relaxed text-slate-400">
              {billTo.address}
              <br />
              {billTo.city}
            </p>
          </div>
          <div className="text-right">
            <p className="text-[8.5px] font-extrabold uppercase tracking-[0.16em] text-slate-400">
              Ship to
            </p>
            <p className="mt-1 text-[10.5px] font-bold text-slate-700">Harborline Logistics</p>
            <p className="mt-0.5 text-[9.5px] leading-relaxed text-slate-400">
              {shipTo(invoice)}
              <br />
              Canada
            </p>
          </div>
        </div>

        <table className="mt-5 w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-slate-200">
              <th className="pb-2 text-[8.5px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                Description
              </th>
              <th className="hidden pb-2 text-right text-[8.5px] font-extrabold uppercase tracking-[0.12em] text-slate-400 sm:table-cell">
                Qty
              </th>
              <th className="hidden pb-2 text-right text-[8.5px] font-extrabold uppercase tracking-[0.12em] text-slate-400 sm:table-cell">
                Unit price
              </th>
              <th className="pb-2 text-right text-[8.5px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                Amount
              </th>
            </tr>
          </thead>
          <tbody>
            {invoice.lineItems.map((item) => (
              <tr key={item.description} className="border-b border-slate-100 last:border-0">
                <td className="py-2 pr-3 text-[10px] font-medium leading-snug text-slate-600">
                  {item.description}
                </td>
                <td className="hidden py-2 text-right text-[10px] tabular-nums text-slate-500 sm:table-cell">
                  {item.qty}
                </td>
                <td className="hidden py-2 text-right text-[10px] tabular-nums text-slate-500 sm:table-cell">
                  {formatMoney(item.unitPrice, invoice.currency)}
                </td>
                <td className="py-2 text-right text-[10px] font-semibold tabular-nums text-slate-700">
                  {formatMoney(item.amount, invoice.currency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="mt-4 flex justify-end">
          <div className="w-full max-w-[250px]">
            <DocTotal label="Subtotal" value={formatMoney(subtotal, invoice.currency)} />
            <DocTotal
              label={invoice.taxLabel}
              value={
                invoice.taxAmount > 0 ? formatMoney(invoice.taxAmount, invoice.currency) : 'Zero-rated'
              }
            />
            <div className="mt-1.5 flex items-center justify-between border-t border-slate-200 pt-2">
              <span className="text-[10px] font-extrabold uppercase tracking-[0.1em] text-slate-500">
                Total {invoice.currency}
              </span>
              <span className="text-[14px] font-extrabold tabular-nums tracking-[-0.02em] text-slate-900">
                {formatMoney(invoice.amount, invoice.currency)}
              </span>
            </div>
          </div>
        </div>

        <div
          className={
            invoice.payment.locked
              ? 'mt-5 flex items-start gap-2.5 rounded-lg border border-rose-100 bg-rose-50/70 px-3 py-2.5'
              : isCard
                ? 'mt-5 flex items-start gap-2.5 rounded-lg border border-sky-100 bg-sky-50/60 px-3 py-2.5'
                : 'mt-5 flex items-start gap-2.5 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5'
          }
        >
          <span
            className={
              invoice.payment.locked
                ? 'mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-white text-rose-500 ring-1 ring-rose-100'
                : isCard
                  ? 'mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-white text-sky-600 ring-1 ring-sky-100'
                  : 'mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-white text-slate-500 ring-1 ring-slate-200'
            }
          >
            <PaymentIcon size={13} strokeWidth={2.1} />
          </span>
          <div className="min-w-0">
            <p className="text-[8.5px] font-extrabold uppercase tracking-[0.14em] text-slate-400">
              Payment
            </p>
            <p className="mt-0.5 text-[10px] font-bold text-slate-700">
              {invoice.payment.locked
                ? 'Payment locked — vendor account unverified'
                : isCard
                  ? `Brim virtual card ${invoice.payment.detail}`
                  : `EFT · ${invoice.payment.detail}`}
            </p>
            <p className="mt-0.5 text-[9px] leading-relaxed text-slate-400">
              {invoice.payment.locked
                ? 'The remit-to account differs from the vendor master. Verify the change by calling the vendor number on file before releasing payment.'
                : isCard
                  ? 'Single-use card, locked to this vendor and amount. Charged when the invoice is approved.'
                  : 'Paid from the Harborline AP operating account. Bank detail changes require a second Admin.'}
            </p>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3">
          <p className="text-[8.5px] font-medium text-slate-300">
            Questions? {invoice.supplierEmail}
          </p>
          <p className="text-[8.5px] font-medium text-slate-300">
            Page 1 of {invoice.attachment.pages} · {invoice.attachment.name}
          </p>
        </div>
      </div>
    </div>
  )
}

function DocMeta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[8.5px] font-extrabold uppercase tracking-[0.14em] text-slate-400">{label}</p>
      <p className="mt-1 text-[10px] font-semibold tabular-nums text-slate-600">{value}</p>
    </div>
  )
}

function DocTotal({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-0.5">
      <span className="text-[9.5px] font-medium text-slate-400">{label}</span>
      <span className="text-[10px] font-semibold tabular-nums text-slate-600">{value}</span>
    </div>
  )
}
