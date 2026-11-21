import { useEffect, useRef, useState } from 'react'
import { FileText, ShieldCheck, UploadCloud, X } from 'lucide-react'
import { cn } from '../lib/cn'

interface UploadModalProps {
  open: boolean
  busy?: boolean
  onClose: () => void
  onSubmit: (file: File | null) => void
}

export function UploadModal({ open, busy = false, onClose, onSubmit }: UploadModalProps) {
  const [file, setFile] = useState<File | null>(null)
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      setFile(null)
      setDragging(false)
    }
  }, [open])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center sm:items-center sm:p-6">
      <div
        className="absolute inset-0 bg-slate-900/30 backdrop-blur-[3px]"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="upload-title"
        className="animate-pop relative w-full max-w-[520px] rounded-t-3xl border border-white/60 bg-white p-5 shadow-2xl shadow-slate-900/20 sm:rounded-3xl sm:p-6"
      >
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-400 to-blue-600 text-white shadow-lg shadow-sky-500/25">
            <UploadCloud size={20} strokeWidth={2} />
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="upload-title" className="text-[17px] font-extrabold tracking-[-0.02em] text-slate-900">
              Upload an invoice
            </h2>
            <p className="mt-0.5 text-[11.5px] leading-relaxed text-slate-400">
              Send a supplier invoice straight into the AP pipeline.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close upload dialog"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={17} />
          </button>
        </div>

        {file ? (
          <div className="mt-5 flex items-center gap-3 rounded-2xl border border-sky-200 bg-sky-50/60 p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-rose-500 shadow-sm ring-1 ring-sky-100">
              <FileText size={18} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[12px] font-bold text-slate-700">{file.name}</p>
              <p className="mt-0.5 text-[10.5px] font-medium text-slate-400">
                {(file.size / 1024).toFixed(0)} KB · ready for intake
              </p>
            </div>
            <button
              type="button"
              onClick={() => {
                setFile(null)
                if (inputRef.current) inputRef.current.value = ''
              }}
              aria-label="Remove file"
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-white hover:text-slate-700"
            >
              <X size={14} />
            </button>
          </div>
        ) : (
          <div
            onDragOver={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setDragging(false)
              const dropped = event.dataTransfer.files?.[0]
              if (dropped) setFile(dropped)
            }}
            className={cn(
              'mt-5 rounded-2xl border-2 border-dashed px-6 py-7 text-center transition',
              dragging
                ? 'border-sky-400 bg-sky-50'
                : 'border-slate-200 bg-slate-50/60 hover:border-sky-300 hover:bg-sky-50/40',
            )}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".pdf,.png,.jpg,.jpeg"
              className="hidden"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-white text-sky-500 shadow-sm ring-1 ring-slate-200/70">
              <UploadCloud size={20} strokeWidth={1.9} />
            </div>
            <p className="mt-3 text-[12.5px] font-bold text-slate-700">
              Drag & drop your invoice
            </p>
            <p className="mt-1 text-[10.5px] font-medium text-slate-400">
              PDF, PNG or JPG · up to 10 MB
            </p>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="mt-3 inline-flex h-8 items-center rounded-lg border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 shadow-sm transition hover:border-slate-300 hover:text-slate-900"
            >
              Browse files
            </button>
          </div>
        )}

        <div className="mt-4 flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2.5">
          <ShieldCheck size={14} className="mt-0.5 shrink-0 text-emerald-500" />
          <p className="text-[10px] leading-relaxed text-slate-400">
            Files are stored by SHA-256 hash and deduplicated against the email message ID, so the
            same invoice is never processed twice.
          </p>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="h-9 rounded-xl px-3.5 text-[11.5px] font-semibold text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onSubmit(file)}
            disabled={!file || busy}
            className="inline-flex disabled:cursor-not-allowed disabled:opacity-50 h-9 items-center gap-1.5 rounded-xl bg-gradient-to-r from-sky-500 to-blue-600 px-4 text-[11.5px] font-semibold text-white shadow-lg shadow-sky-500/25 transition hover:brightness-105 active:scale-[0.98]"
          >
            <UploadCloud size={14} />
            {busy ? 'Uploading…' : file ? 'Upload & process' : 'Choose a file first'}
          </button>
        </div>
      </div>
    </div>
  )
}
