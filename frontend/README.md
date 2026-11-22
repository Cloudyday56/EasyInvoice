# EasyInvoice Web

The React front end for EasyInvoice, the card-first AP automation inbox.

This milestone delivers the inbox experience: a focused sidebar, a searchable invoice list, and a
preview that opens on the actual invoice document with one obvious payment action.

## Stack

- React 19 + TypeScript
- Vite 8
- Tailwind CSS 4
- Lucide icons

## Run locally

```bash
cd frontend
npm install
npm run dev
```

The app runs at <http://localhost:5173>. Requests to `/api` are proxied to the Spring Boot
backend on <http://localhost:8080>.

```bash
npm run build    # type-check + production build
npm run preview  # serve the production build
npm run lint     # TypeScript only
```

## Docker

The whole app (db, backend, frontend) is started from the repo root:

```bash
docker compose up --build
```

Open <http://localhost:5173>. `nginx.conf` serves the static build and proxies `/api` to the `backend`
compose service.

That file mounts `nginx.host.conf`, which proxies `/api` to `host.docker.internal:8080`.

## Structure

```
src/
  App.tsx                    inbox state, filtering, approve/hold/release flows
  components/
    Sidebar.tsx              three queues + payment rail
    InboxList.tsx            searchable invoice list with compact cards
    PreviewPanel.tsx         risk overview, facts, document/email tabs, action bar
    InvoiceDocument.tsx      paper-style invoice preview with payment details
    AuditTrailDrawer.tsx     human-readable activity timeline + integrity banner
    UploadModal.tsx          drag-and-drop invoice intake
    ReasonModal.tsx          written reason for approve / reject / return / resolve / release
    ui.tsx                   avatars, tags, status/flag/risk pills
  api/client.ts              typed fetch client for /api (sends X-User-Id)
  api/adapt.ts               maps backend DTOs onto the UI's Invoice view model, audit labels
  types.ts                   invoice and filter types
  lib/filters.ts             queue labels
Dockerfile                   multi-stage build (Node build → nginx serve)
nginx.conf                   SPA fallback + /api proxy to the backend service
```

## What the UI makes obvious

- **Three queues only:** `Needs action`, `Fraud & holds`, and `Paid`. There are no dead `VIEWS`
  links, and the sidebar shows the active Brim corporate card (`•• 4821`).
- **Payment method beside the facts:** the summary grid puts the card/EFT rail next to amount,
  tax, due date and PO number. Bank details waiting on review are called out explicitly.
- **One clear primary action:** the bottom action bar uses a full sentence, for example
  `Approve ($12,500.00)`, `Release payment ($12,500.00)` or `Mark issue resolved`. The
  `Audit trail` button sits beside it.
- **Document first:** the risk overview stays at the top, the invoice PDF preview renders
  directly underneath, and the raw email is one tab away under `Original email`.
- **Activity without jargon:** open **Activity & audit trail** from the preview header or footer
  to see a simple timeline in chronological order: what happened, who did it, why, and which step
  it affected. It defaults to **Key activity** and can switch to **All activity**; the header always
  shows how many events are in view. Long histories include **Jump to latest**, and hashes, event
  codes and JSON live under **Technical details**, so they never get in the way.

## Notes

- All data comes from the Spring Boot API. The list polls every 2.5 s and the selected invoice every
  1.2 s while it is still moving, so the step tracker fills in live.
- The user switcher (bottom of the sidebar) is the demo login: it sets the `X-User-Id` header.
  Buttons follow the backend's `allowedActions`, so role, limit and separation-of-duty rules come
  from the server; refused actions show the server's reason in a red toast.
- Releasing a payment asks for an optional note. Approve, reject, return and resolve require a
  reason; all of them appear in the activity trail.
- Admins get **Records & tools**: poll inbox, review audit activity, check audit integrity, download
  the audit log, test the document reader, clear invoices and reset demo data.
- The audit drawer shows an integrity banner when opened from **Check audit integrity**. CSV
  download is scoped to the open invoice from the drawer, or to all records from the sidebar.
- Responsive behaviour: inbox + preview from 1024px, a single-pane inbox with a slide-over menu
  and a back button on phones.
