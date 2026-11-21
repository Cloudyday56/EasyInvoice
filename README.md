# EasyInvoice

Accounts payable automation for Brim business cards (CodeJam16). Every invoice moves through
**10 single-purpose pipeline nodes**, from inbox to settled payout, with role checks and a
hash-chained audit trail at every step.

```
backend/    Java 21 · Spring Boot 4 · plain SQL (JdbcTemplate) · PostgreSQL
frontend/   React + JavaScript · Vite
data/       sample dataset (CSV history, inbox emails, incoming invoice files)
```

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

On first start the backend creates the schema (`backend/src/main/resources/schema.sql`) and
loads `/data` into it. Data persists in a Docker volume; **Audit & tools → Reset demo** wipes
everything and reloads it. `docker compose down -v` deletes the volumes too.

### LLM (OpenRouter)

```bash
cp .env.example .env      # then put your key in OPENROUTER_API_KEY
docker compose up --build
```

- `OPENROUTER_API_KEY`: read from the environment only, never stored in code.
- `LLM_MODEL`: any OpenRouter model id (default `anthropic/claude-sonnet-4.5`). Extraction needs a vision model.
- `EXTRACTOR=fake|llm`: `fake` (default) invents data and makes no network calls; `llm` reads the document with the model.

To check the key works: log in as an Admin → **Audit & tools → Test LLM connection**
(or `POST /api/admin/llm/test`). The client is `backend/.../llm/LlmClient.java`.

### Local development (without the app containers)

```bash
docker compose up db                       # just Postgres
cd backend && ./mvnw spring-boot:run       # API on :8080 (reads ../data)
cd frontend && npm install && npm run dev  # UI on :5173, proxies /api to :8080
cd backend && ./mvnw test                  # unit tests
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
- **Tamper evidence.** The DB rejects `UPDATE`/`DELETE` on `audit_events` (trigger). If a row is changed anyway, verify names the broken row.

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
| Seed | `db/SeedLoader.java`, `PolicySeeds.java` | CSV → tables; reset demo |

Outcomes: `PASS` / `FLAG` go to the next node. `BLOCK` parks the invoice in a queue. `WAIT` means
waiting for a person, or re-checking the bank. `IGNORE` means not an invoice.

**Login** is a user switcher: the UI sends `X-User-Id`, and the server re-reads that user's role
and limits on every request. No passwords, because this is a demo.

### Roles (dataset → app)

| users.csv role | App role | Notes |
|---|---|---|
| employee | EMPLOYEE | uploads, sees own invoices |
| approver | APPROVER | own department, up to `approval_limit_cad` |
| ap_clerk, finance_manager, controller_admin | ADMIN | release limit: clerk 0, finance manager 100k, controller = their approval limit |
| auditor | ADMIN (read-only) | can view everything, change nothing |

### Data notes

- Historical invoices (426) are loaded with their final status. They did not run through the pipeline.
- The history has **6 repeated (vendor, invoice number) pairs**. The unique constraint would refuse them, so the later copy is loaded without a canonical number and gets a `HISTORICAL_REPEAT_NUMBER` flag. They are logged at startup.
- Payments with status `outstanding` (uncashed cheques) are loaded as `SENT`. The original value is kept in `source_status`.
- `approval_policies.csv` is written in prose, so it was hand-translated to JSON in `PolicySeeds.java`, with the original text kept.
