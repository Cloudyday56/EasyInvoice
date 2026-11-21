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

## Docker (frontend only)

The frontend can be built and served as a static production bundle through nginx:

```bash
cd frontend
docker compose up --build
```

Open <http://localhost:5173>. Stop it with `docker compose down`.

`nginx.conf` proxies `/api` to `http://host.docker.internal:8080`, so a Spring Boot backend
running on the host is reachable from the container. If the backend runs in another container,
point `proxy_pass` at that compose service name instead and place both services on a shared network.

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
  data/invoices.ts           realistic Harborline AP inbox (CAD + USD, GST/HST/QST)
  types.ts                   invoice and filter types
  lib/filters.ts             queue labels
Dockerfile                   multi-stage build (Node build → nginx serve)
docker-compose.yml           frontend-only service on port 5173
nginx.conf                   SPA fallback + /api proxy to the host backend
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

- The mock dataset mirrors the backend contract: invoice statuses, pipeline nodes (`N4`–`N10`),
  risk scores, payment rails and exception reasons.
- Everything is client-side for now. The next step is to replace `src/data/invoices.ts`
  with API calls and wire the actions to the Spring Boot endpoints.
- Responsive behaviour: inbox + preview from 1024px, a single-pane inbox with a slide-over menu
  and a back button on phones.
