# EasyInvoice Web

The React front end for EasyInvoice, the card-first AP automation inbox for Harborline Logistics.

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
compose service. To run only the frontend container against a backend on your host, change `proxy_pass`
to `http://host.docker.internal:8080/api/`.

## Structure

```
src/
  App.tsx                    inbox state, filtering, approve/hold/release/reply flows
  components/
    Sidebar.tsx              three queues + payment rail
    InboxList.tsx            searchable invoice list with compact cards
    PreviewPanel.tsx         AI summary, facts, document/email tabs, action bar
    InvoiceDocument.tsx      paper-style invoice preview with payment details
    UploadModal.tsx          drag-and-drop invoice intake
    ui.tsx                   avatars, tags, status/flag/risk pills
  api/client.ts              typed fetch client for /api (sends X-User-Id)
  api/adapt.ts               maps backend DTOs onto the UI's Invoice view model
  components/ReasonModal.tsx written reason for approve / reject / return / resume
  types.ts                   invoice and filter types
  lib/filters.ts             queue labels
Dockerfile                   multi-stage build (Node build → nginx serve)
nginx.conf                   SPA fallback + /api proxy to the backend service
```

## What the UI makes obvious

- **Three queues only:** `Needs action`, `Fraud & holds`, and `Paid`. There are no dead `VIEWS`
  links, and the sidebar shows the active Brim corporate card (`•• 4821`).
- **Payment method beside the facts:** the summary grid puts the card/EFT rail next to amount,
  tax, due date and PO number. Locked bank details are called out explicitly.
- **One clear primary action:** the bottom action bar uses a full sentence, for example
  `Approve & Pay with Card ($12,500.00)` or `Hold & Verify Vendor`. There is no icon-only rail.
  Secondary `Assign`, `Reply`, and `Hold` buttons sit beside it.
- **Document first:** the AI Fraud Summary stays at the top, the invoice PDF preview renders
  directly underneath, and the raw email is one tab away under `Original email`.

## Notes

- All data comes from the Spring Boot API. The list polls every 2.5 s and the selected invoice every
  1.2 s while it is still moving, so the 10-node tracker fills in live.
- The user switcher (bottom of the sidebar) is the demo login: it sets the `X-User-Id` header.
  Buttons follow the backend's `allowedActions`, so role, limit and separation-of-duty rules come
  from the server; refused actions show the server's reason in a red toast.
- Admins get an **Audit & tools** section: poll inbox, verify chain, export log, test LLM, reset demo.
- Responsive behaviour: inbox + preview from 1024px, a single-pane inbox with a slide-over menu
  and a back button on phones.
