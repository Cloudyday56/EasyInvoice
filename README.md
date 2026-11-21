# EasyInvoice

Accounts payable automation for Brim business cards (CodeJam16). Every invoice moves through
**10 single-purpose pipeline nodes**, from inbox to settled payout, with role checks and a
hash-chained audit trail at every step.

```
backend/    Java 21 · Spring Boot 4 · Spring Data JPA (Hibernate) · PostgreSQL
frontend/   React + JavaScript · Vite
data/       sample dataset (CSV history, inbox emails, incoming invoice files): NOT in git, see below
```

## Sample data (not in git)

`data/` is git-ignored. Copy the `brim_datasets` folder contents into `data/` before running, so that
`data/data/users.csv`, `data/inbox/emails.json` and `data/incoming_invoices/` exist. Without it the database starts empty.

## Run it (one command)

```bash
docker compose up --build
```

Three containers start:

| Container | URL | What |
|---|---|---|
| `frontend` | http://localhost:5173 | React build served by nginx; `/api` is proxied to the backend |
| `backend` | http://localhost:8080 | Spring Boot API + pipeline runner |
| `db` | localhost:5432 | Postgres 16 (db/user/password `easyinvoice`) |

On first start Hibernate creates the tables from the entities in `backend/.../model` (`ddl-auto=update`; there is no
SQL schema file), and the backend loads `/data` into the database. Data persists in a Docker volume; **Audit & tools → Reset demo** wipes
everything and reloads it. `docker compose down -v` deletes the volumes too.

### LLM (OpenRouter)

```bash
cp .env.example .env      # then put your key in OPENROUTER_API_KEY
docker compose up --build
```

- `OPENROUTER_API_KEY`: read from the environment only, never stored in code.
- `LLM_MODEL`: any OpenRouter model id (default `anthropic/claude-sonnet-4.5`). Extraction needs a vision model.
- `EXTRACTOR=manual|llm`: `manual` (default) reads the PDF's text layer with rules and makes no network calls (scans and images cannot be read: they land in the extraction review queue); `llm` reads the document with the model.

To check the key works: log in as an Admin → **Audit & tools → Test LLM connection**
(or `POST /api/admin/llm/test`). The client is `backend/.../llm/LlmClient.java`.

### Fraud scoring

Node 6 (`FRAUD_SCORING`) scores every invoice from 0–100 using weighted signals:

| Signal | Default points | What it checks |
|---|---:|---|
| `BANK_DETAILS_MISMATCH` | 100 | Remit-to bank on the invoice vs. the vendor master — **immediate block** |
| `UNVERIFIED_BANK_CHANGE` | 45 | Bank change in the last 30 days without a verified callback |
| `LOOKALIKE_SENDER_DOMAIN` | 45 | Sender domain resembles the vendor / AP contact domain |
| `FREE_MAIL_DOMAIN` | 25 | Sender uses a free-mail provider |
| `SENDER_DOMAIN_MISMATCH` | 10 | Sender domain differs from the vendor's AP contact domain |
| `DUPLICATE_INVOICE_NUMBER` | 30 | Same vendor + canonical invoice number already exists |
| `SPLIT_INVOICE` | 25 | Another similar invoice within ±3 days, both under $10k |
| `AMOUNT_OUTLIER` | 20 | Total more than 3σ above ≥5 prior vendor invoices |

Score `< passBelow` (40) → `PASS`, `40–69` → `FLAG` (warnings travel with the invoice),
`>= blockAt` (70) or a bank mismatch → `BLOCK` into **Needs attention → Fraud hold**.
Resolving a fraud hold marks its reviewed signals as overridden and re-enters at `FRAUD_SCORING`,
so the node skips those signal codes and only new evidence can block the invoice again.
`riskScore`, signal codes and per-signal points are written to the hash-chained audit event.

All thresholds, weights, windows and free-mail domains live under `app.fraud.*` in
`backend/src/main/resources/application.yaml` (bound by `pipeline/FraudScoringConfig.java`).
Override them with environment variables, e.g. `APP_FRAUD_BLOCK_AT=80`.

### Local development (without the app containers)

The entities use Lombok. In IntelliJ, enable *Settings → Build → Compiler → Annotation Processors → Enable annotation processing*.

```bash
docker compose up db                       # just Postgres
cd backend && ./mvnw spring-boot:run       # API on :8080 (reads ../data)
cd frontend && npm install && npm run dev  # UI on :5173, proxies /api to :8080
cd backend && ./mvnw test                  # unit tests + an end-to-end test on an embedded Postgres (no Docker needed)
```

## Demo script

1. Pick **Aiden Grant** (Operations employee) in the user switcher, then **Upload invoice** (any PDF from `data/incoming_invoices/`).
2. The invoice page shows the 10-node tracker filling in live. It stops at **Approval execution**.
3. Switch to **Dev Malhotra** (Operations manager) → **My approvals** → Approve.
4. Switch to **Elise Tremblay** (finance manager, Admin) → **Payments** → Release.
   The mock bank reports SENT, then SETTLED about 4 s later, and the invoice becomes **PAID**.
5. Open the invoice's audit trail, or **Audit & tools → Verify chain** / Export CSV.

Things to show the judges:
- **Approver ≠ releaser.** Upload as **Kavya Morrison** (Facilities). The manager Liam is on leave, so routing assigns the delegate **Elise** (`acting_for` is recorded). After Elise approves, she is refused the release, so **Marcus Webb** releases it.
- **Exception queue + resume.** Upload a file whose name contains `block-<node>`, e.g. `test_block-vendor-match.pdf`. It parks in **Needs attention → Vendor hold**. Resume it with a reason and it re-enters at the right node, with the override kept on the flag.
- **Inbox.** **Audit & tools → Poll AP inbox** ingests `emails.json`. Email has no requester, so most items land in **Routing error**. Emails without attachments become **Missing document** or **Ignored mail**.
- **Tamper evidence.** Each audit event stores the hash of the one before it. If any row is changed or removed, **Verify chain** names the first broken row.

## How it fits together

```
upload / inbox ──► IntakeService ──► jobs table ──► PipelineRunner (polls every 0.5 s)
                                                     │  runs Node.run(invoice, ctx) -> StageResult
                                                     │  applies patch/flags, moves status (InvoiceStateMachine),
                                                     │  writes audit (AuditService), queues the next node
human actions (approve, resume, release) ──► HumanActionService ──► authorize() ──► re-queue a node
```

| Piece | File | One-line role |
|---|---|---|
| Node contract | `pipeline/Node.java`, `StageResult.java`, `Outcome.java` | `run(invoice, ctx) -> {outcome, flags, patch, reasonCode}` |
| Ordering | `pipeline/NodeName.java` | Enum order = pipeline order; status on pass; failure queue |
| Queues | `pipeline/HoldQueue.java` | 8 exception queues, each with its `resume_node` |
| Runner | `pipeline/PipelineRunner.java` | Job loop, retries with backoff, BLOCK → hold, audit |
| Status | `pipeline/InvoiceStateMachine.java` | The only writer of `invoices.status` |
| Nodes | `pipeline/nodes/*Node.java` | The 10 steps |
| Security | `security/AuthorizationService.java` | `authorize(user, action, invoice)`, rules in pure `decide()` |
| Audit | `audit/AuditHasher.java`, `AuditService.java` | `hash = sha256(prev_hash + row)`, verify, CSV |
| Data access | `model/*` (entities), `repo/*` (Spring Data repositories) | Plain JPA: one entity and one repository per table the app reads or writes |
| Seed | `db/SeedLoader.java`, `PolicySeeds.java` | Each CSV row → its entity (Jackson) → saved with JPA; reset demo |

Outcomes: `PASS` / `FLAG` go to the next node. `BLOCK` parks the invoice in a queue. `WAIT` means
waiting for a person, or re-checking the bank. `IGNORE` means not an invoice.

There is no hand-written SQL: all 22 tables are JPA entities, and the code uses Spring Data repositories.
Three things that used to need SQL are done with JPA features instead:
- **Job queue:** `@Lock(PESSIMISTIC_WRITE)` with lock timeout -2, which Hibernate turns into `FOR UPDATE SKIP LOCKED`.
- **Audit append lock:** the single `audit_head` row is locked while an event is appended.
- **Invoice and payment numbers (EI-00001, EP-00001):** the id of an `InvoiceNumber` / `PaymentNumber` row, which comes from a JPA `@SequenceGenerator`.

Trade-offs of letting Hibernate own the schema: there are no foreign keys (the entities hold plain id columns), and
**Reset demo** empties the tables but does not restart the number sequences. If you have an older Docker volume from
before this change, run `docker compose down -v` once so Hibernate starts from a clean database.

**Login** is a user switcher: the UI sends `X-User-Id`, and the server re-reads that user's role
and limits on every request. No passwords, because this is a demo.

### Roles (dataset → app)

| users.csv role | App role | Notes |
|---|---|---|
| employee, ap_clerk, auditor | EMPLOYEE | uploads, sees own invoices; no approval or release authority |
| approver | APPROVER | own department, up to `approval_limit_cad`; cannot release payments |
| finance_manager, controller_admin | ADMIN | approves the higher tiers (up to `approval_limit_cad`), releases any payment, sees everything |

Two rules hold for every role: nobody approves an invoice they requested (it escalates to their manager), and
anyone who approved a step of an invoice cannot release its payment. A delegate's actions are recorded as "acting for".

### Data notes

- Historical invoices (426) are loaded with their final status. They did not run through the pipeline.
- The history has **6 repeated (vendor, invoice number) pairs**. The unique constraint would refuse them, so the later copy is loaded without a canonical number and gets a `HISTORICAL_REPEAT_NUMBER` flag. They are logged at startup.
- Payments with status `outstanding` (uncashed cheques) are loaded as `SENT`. The original value is kept in `source_status`.
- `approval_policies.csv` is written in prose, so it was hand-translated to JSON in `PolicySeeds.java`, with the original text kept.
