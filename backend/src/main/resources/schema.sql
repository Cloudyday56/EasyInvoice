-- EasyInvoice schema. Run on every startup by DatabaseInitializer; every statement is idempotent.
-- Seeded master data keeps the ids from /data (U001, V001, AP-00001...). New rows get generated ids.

CREATE SEQUENCE IF NOT EXISTS new_invoice_seq;
CREATE SEQUENCE IF NOT EXISTS new_payment_seq;

-- ---------- Master data ----------

CREATE TABLE IF NOT EXISTS departments (
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    cost_centre        TEXT,
    annual_budget_cad  NUMERIC(14,2),
    manager_user_id    TEXT
);

CREATE TABLE IF NOT EXISTS users (
    id                  TEXT PRIMARY KEY,
    full_name           TEXT NOT NULL,
    email               TEXT,
    title               TEXT,
    department_id       TEXT REFERENCES departments(id),
    manager_id          TEXT,
    role                TEXT NOT NULL CHECK (role IN ('EMPLOYEE','APPROVER','ADMIN')),
    source_role         TEXT,            -- role as written in users.csv (ap_clerk, auditor...)
    read_only           BOOLEAN NOT NULL DEFAULT FALSE,  -- auditor: Admin who can only look
    approval_limit_cad  NUMERIC(14,2) NOT NULL DEFAULT 0,
    release_limit_cad   NUMERIC(14,2) NOT NULL DEFAULT 0,
    work_province       TEXT,
    status              TEXT NOT NULL DEFAULT 'active',  -- active | on_leave | terminated
    status_since        DATE,
    delegate_user_id    TEXT,
    hire_date           DATE
);

CREATE TABLE IF NOT EXISTS gl_accounts (
    account_number  TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    account_type    TEXT,
    notes           TEXT
);

CREATE TABLE IF NOT EXISTS vendors (
    id                        TEXT PRIMARY KEY,
    legal_name                TEXT NOT NULL,
    category                  TEXT,
    default_gl_account        TEXT,
    address                   TEXT,
    province_or_state         TEXT,
    country                   TEXT,
    gst_hst_number            TEXT,
    qst_number                TEXT,
    payment_terms_days        INT,
    early_pay_discount        TEXT,
    preferred_payment_method  TEXT,
    accepts_card_payment      BOOLEAN NOT NULL DEFAULT FALSE,
    bank_institution          TEXT,
    bank_transit              TEXT,
    bank_account              TEXT,
    bank_details_updated_at   DATE,
    ap_contact_email          TEXT,
    requires_po               BOOLEAN NOT NULL DEFAULT FALSE,
    status                    TEXT NOT NULL,   -- active | pending_review | on_hold | blocked
    onboarded_at              DATE
);

CREATE TABLE IF NOT EXISTS bank_change_requests (
    id                    TEXT PRIMARY KEY,
    vendor_id             TEXT NOT NULL REFERENCES vendors(id),
    changed_at            DATE NOT NULL,
    changed_by_user_id    TEXT,
    request_channel       TEXT,
    verified_by_callback  BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by_user_id   TEXT,     -- must be a different Admin than changed_by_user_id
    old_account_last4     TEXT,
    new_account_last4     TEXT,
    status                TEXT NOT NULL DEFAULT 'PENDING'  -- PENDING | VERIFIED | REJECTED
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id                 TEXT PRIMARY KEY,
    vendor_id          TEXT REFERENCES vendors(id),
    department_id      TEXT REFERENCES departments(id),
    requester_user_id  TEXT,
    issue_date         DATE,
    description        TEXT,
    total_amount       NUMERIC(14,2),
    currency           TEXT,
    status             TEXT,   -- open | partially_invoiced | closed
    approved_by        TEXT
);

CREATE TABLE IF NOT EXISTS contracts (
    id                      TEXT PRIMARY KEY,
    vendor_id               TEXT REFERENCES vendors(id),
    description             TEXT,
    billing_type            TEXT,
    monthly_fee             NUMERIC(14,2),
    currency                TEXT,
    hourly_rate_cap         NUMERIC(14,2),
    fuel_surcharge_cap_pct  NUMERIC(6,2),
    not_to_exceed_monthly   NUMERIC(14,2),
    start_date              DATE,
    end_date                DATE,
    auto_renew              BOOLEAN,
    status                  TEXT
);

CREATE TABLE IF NOT EXISTS tax_codes (
    code         TEXT PRIMARY KEY,
    description  TEXT,
    rate         NUMERIC(8,5) NOT NULL,
    applies_to   TEXT,
    recoverable  TEXT,
    gl_account   TEXT
);

CREATE TABLE IF NOT EXISTS fx_rates (
    date        DATE PRIMARY KEY,
    usd_to_cad  NUMERIC(10,4) NOT NULL
);

-- Approval rules as JSON, evaluated in sort_order by the Approval routing node.
CREATE TABLE IF NOT EXISTS policies (
    id           TEXT PRIMARY KEY,
    rule_type    TEXT NOT NULL,      -- approval | payment_release | match
    rule_json    JSONB,              -- e.g. {"if": {"field": "total_cad", "op": ">", "value": 10000}, "add_step": "ADMIN"}
    source_text  TEXT,               -- the original condition from approval_policies.csv
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order   INT NOT NULL DEFAULT 0
);

-- ---------- Invoices ----------

-- One row per received file (upload or email attachment). The file itself lives on disk under its SHA-256.
CREATE TABLE IF NOT EXISTS source_documents (
    id               BIGSERIAL PRIMARY KEY,
    channel          TEXT NOT NULL,   -- UPLOAD | EMAIL
    email_id         TEXT,
    idempotency_key  TEXT NOT NULL UNIQUE,   -- email_id (or 'upload') + ':' + sha256
    file_name        TEXT,
    content_type     TEXT,
    size_bytes       BIGINT,
    sha256           TEXT,
    storage_path     TEXT,
    sender           TEXT,
    subject          TEXT,
    body             TEXT,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by      TEXT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS invoices (
    id                        TEXT PRIMARY KEY,
    source_document_id        BIGINT REFERENCES source_documents(id),
    document_type             TEXT NOT NULL DEFAULT 'INVOICE',  -- INVOICE | CREDIT_NOTE
    vendor_id                 TEXT REFERENCES vendors(id),
    vendor_name_raw           TEXT,      -- vendor name as read from the document
    vendor_invoice_number     TEXT,
    canonical_invoice_number  TEXT,      -- upper-case, no spaces/dashes/leading zeros
    invoice_date              DATE,
    received_date             DATE,
    due_date                  DATE,
    currency                  TEXT,
    subtotal                  NUMERIC(14,2),
    gst_hst_amount            NUMERIC(14,2),
    pst_qst_amount            NUMERIC(14,2),
    total                     NUMERIC(14,2),
    fx_rate_to_cad            NUMERIC(10,4),
    total_cad                 NUMERIC(14,2),
    department_id             TEXT REFERENCES departments(id),
    requester_user_id         TEXT REFERENCES users(id),
    po_id                     TEXT,
    contract_id               TEXT,
    ship_to_province          TEXT,
    risk_score                INT,
    status                    TEXT NOT NULL,  -- changed only through InvoiceStateMachine
    source_status             TEXT,           -- status as written in invoices.csv (history only)
    hold_queue                TEXT,           -- set when status = ON_HOLD
    hold_reason               TEXT,
    resume_node               TEXT,           -- node the invoice re-enters at once the hold is cleared
    current_node              TEXT,           -- last node that ran
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Repeat guard: the same vendor cannot bill the same invoice number twice.
    CONSTRAINT uq_vendor_invoice_number UNIQUE (vendor_id, canonical_invoice_number)
);

CREATE TABLE IF NOT EXISTS invoice_lines (
    id             BIGSERIAL PRIMARY KEY,
    invoice_id     TEXT NOT NULL REFERENCES invoices(id),
    line_no        INT NOT NULL,
    description    TEXT,
    quantity       NUMERIC(14,4),
    unit_price     NUMERIC(14,4),
    amount         NUMERIC(14,2),
    gl_account     TEXT,
    department_id  TEXT,
    gst_hst_code   TEXT,
    pst_qst_code   TEXT,
    line_type      TEXT
);

-- Flags travel with the invoice. severity FLAG = warning, BLOCK = stopped the line.
CREATE TABLE IF NOT EXISTS flags (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       TEXT NOT NULL REFERENCES invoices(id),
    node             TEXT NOT NULL,
    code             TEXT NOT NULL,
    severity         TEXT NOT NULL CHECK (severity IN ('FLAG','BLOCK')),
    message          TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    overridden_by    TEXT,
    override_reason  TEXT
);

CREATE TABLE IF NOT EXISTS approval_steps (
    id                BIGSERIAL PRIMARY KEY,
    invoice_id        TEXT NOT NULL REFERENCES invoices(id),
    step_no           INT NOT NULL,
    required_role     TEXT NOT NULL,      -- APPROVER | ADMIN
    reason            TEXT,               -- why this step exists (policy, flag, escalation)
    assigned_user_id  TEXT REFERENCES users(id),
    acting_for        TEXT REFERENCES users(id),  -- original approver when a delegate is assigned
    status            TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | RETURNED | VOID
    acted_by          TEXT REFERENCES users(id),
    acted_at          TIMESTAMPTZ,
    comment           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Payments ----------

CREATE TABLE IF NOT EXISTS payments (
    id               TEXT PRIMARY KEY,
    invoice_id       TEXT NOT NULL REFERENCES invoices(id),
    vendor_id        TEXT REFERENCES vendors(id),
    method           TEXT NOT NULL,     -- EFT | CHEQUE | CARD
    scheduled_date   DATE,
    paid_date        DATE,
    currency         TEXT,
    amount           NUMERIC(14,2),
    fx_rate_to_cad   NUMERIC(10,4),
    amount_cad       NUMERIC(14,2),
    discount_taken   NUMERIC(14,2),
    reference        TEXT,
    status           TEXT NOT NULL,     -- SCHEDULED | RELEASED | SENT | SETTLED | FAILED
    source_status    TEXT,              -- status as written in payments.csv (history only)
    idempotency_key  TEXT UNIQUE,       -- = payment id; the bank adapter refuses a second send
    prepared_by      TEXT,
    released_by      TEXT,
    released_at      TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    failure_reason   TEXT
);

CREATE TABLE IF NOT EXISTS virtual_cards (
    id                 BIGSERIAL PRIMARY KEY,
    payment_id         TEXT NOT NULL REFERENCES payments(id),
    masked_number      TEXT NOT NULL,
    amount_limit       NUMERIC(14,2) NOT NULL,
    merchant_lock      TEXT NOT NULL REFERENCES vendors(id),
    expires_at         TIMESTAMPTZ NOT NULL,
    single_use         BOOLEAN NOT NULL DEFAULT TRUE,
    status             TEXT NOT NULL DEFAULT 'ISSUED'   -- ISSUED | USED | EXPIRED
);

CREATE TABLE IF NOT EXISTS card_transactions (
    id                   TEXT PRIMARY KEY,
    card_id              TEXT,
    cardholder_user_id   TEXT,
    posted_date          DATE,
    merchant_descriptor  TEXT,
    mcc                  TEXT,
    amount               NUMERIC(14,2),
    currency             TEXT,
    amount_cad           NUMERIC(14,2),
    source               TEXT,     -- employee_card | ap_virtual_card
    receipt_attached     BOOLEAN,
    memo                 TEXT,
    payment_id           TEXT,     -- set for virtual-card payments made by this app
    invoice_id           TEXT
);

-- Bank statement from /data, kept for reconciliation later.
CREATE TABLE IF NOT EXISTS bank_statement_lines (
    id           TEXT PRIMARY KEY,
    posted_date  DATE,
    description  TEXT,
    debit        NUMERIC(14,2),
    credit       NUMERIC(14,2)
);

-- ---------- Runtime ----------

CREATE TABLE IF NOT EXISTS notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id),
    template    TEXT NOT NULL,
    invoice_id  TEXT,
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The pipeline's work queue: "run node X on invoice Y, not before run_after".
CREATE TABLE IF NOT EXISTS jobs (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  TEXT NOT NULL REFERENCES invoices(id),
    node        TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    attempts    INT NOT NULL DEFAULT 0,
    run_after   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_jobs_pending ON jobs (status, run_after);

-- Append-only, hash-chained. hash = sha256(prev_hash + row). See AuditHasher.
-- payload_json is TEXT (not JSONB) so the stored bytes are exactly what was hashed.
CREATE TABLE IF NOT EXISTS audit_events (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    TEXT,
    payment_id    TEXT,
    actor_id      TEXT,        -- user id, or 'system' for the pipeline
    acting_for    TEXT,
    role          TEXT,
    action        TEXT NOT NULL,
    node          TEXT,
    from_status   TEXT,
    to_status     TEXT,
    payload_json  TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    prev_hash     TEXT NOT NULL,
    hash          TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_invoice ON audit_events (invoice_id);

-- The database itself refuses to change or delete audit rows.
-- (TRUNCATE is still allowed: the "reset demo" button uses it to start over.)
CREATE OR REPLACE FUNCTION audit_events_are_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_append_only ON audit_events;
CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_are_append_only();
