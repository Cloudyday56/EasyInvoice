package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.InvoiceNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads the sample dataset from data/data/*.csv into the tables, in foreign-key order.
 *
 * Values go in as text and Postgres casts them to the column type (the JDBC URL sets
 * stringtype=unspecified). Empty cells become NULL. "yes"/"no" are valid Postgres booleans.
 *
 * Historical invoices are loaded with their final status; they do not go through the pipeline.
 */
@Component
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Path csvDir;

    public SeedLoader(JdbcTemplate jdbc, AuditService audit, @Value("${app.data-dir}") String dataDir) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.csvDir = Path.of(dataDir).resolve("data");
    }

    public void seedAll() {
        log.info("Seeding from {}", csvDir.toAbsolutePath());
        Map<String, Object> counts = new LinkedHashMap<>();

        counts.put("departments", load("departments.csv", "departments", row -> rename(row, "department_id", "id")));
        counts.put("users", load("users.csv", "users", SeedLoader::userRow));
        counts.put("gl_accounts", load("gl_accounts.csv", "gl_accounts", row -> new LinkedHashMap<>(row)));
        counts.put("vendors", load("vendors.csv", "vendors", row -> rename(row, "vendor_id", "id")));
        counts.put("bank_change_requests", load("bank_change_history.csv", "bank_change_requests", SeedLoader::bankChangeRow));
        counts.put("purchase_orders", load("purchase_orders.csv", "purchase_orders", row -> rename(row, "po_id", "id")));
        counts.put("contracts", load("contracts.csv", "contracts", row -> rename(row, "contract_id", "id")));
        counts.put("tax_codes", load("tax_codes.csv", "tax_codes", row -> new LinkedHashMap<>(row)));
        counts.put("fx_rates", load("fx_rates.csv", "fx_rates", row -> new LinkedHashMap<>(row)));
        counts.put("policies", PolicySeeds.insertAll(jdbc));

        Map<String, String> firstInvoiceByNumber = new HashMap<>();
        List<String[]> repeats = new ArrayList<>();
        counts.put("invoices", load("invoices.csv", "invoices", row -> invoiceRow(row, firstInvoiceByNumber, repeats)));
        flagHistoricalRepeats(repeats);

        counts.put("invoice_lines", load("invoice_lines.csv", "invoice_lines", row -> new LinkedHashMap<>(row)));
        counts.put("approval_steps", load("approvals.csv", "approval_steps", SeedLoader::approvalRow));
        counts.put("payments", load("payments.csv", "payments", SeedLoader::paymentRow));
        counts.put("card_transactions", load("card_transactions.csv", "card_transactions", row -> rename(row, "txn_id", "id")));
        counts.put("bank_statement_lines", load("bank_statement.csv", "bank_statement_lines", row -> rename(row, "bank_txn_id", "id")));

        // TODO: inbox/emails.json and incoming_invoices/ are NOT seeded on purpose:
        //       they are unprocessed mail. An Admin pulls them in with "Poll inbox" (POST /api/admin/poll-inbox).

        log.info("Seed complete: {}", counts);
        audit.system(null, "seed.loaded", null, null, null, counts);
    }

    // ---------- generic loader ----------

    /** Reads one CSV, converts each row with toRow, and batch-inserts it. Returns the row count. */
    private int load(String fileName, String table, Function<Map<String, String>, Map<String, Object>> toRow) {
        Path file = csvDir.resolve(fileName);
        if (!Files.exists(file)) {
            log.warn("TODO seed: {} not found, table {} left empty", file, table);
            return 0;
        }
        List<Map<String, Object>> rows = CsvReader.read(file).stream().map(toRow).toList();
        if (rows.isEmpty()) return 0;

        List<String> columns = new ArrayList<>(rows.getFirst().keySet());
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", columns.stream().map(c -> "?").toList()) + ")";
        List<Object[]> values = rows.stream()
                .map(row -> columns.stream().map(c -> emptyToNull(row.get(c))).toArray())
                .toList();
        jdbc.batchUpdate(sql, values);
        return rows.size();
    }

    private static Object emptyToNull(Object value) {
        return (value instanceof String s && s.isEmpty()) ? null : value;
    }

    private static Map<String, Object> rename(Map<String, String> row, String from, String to) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(key.equals(from) ? to : key, value));
        return out;
    }

    // ---------- per-table conversions ----------

    /** users.csv has 6 roles; we keep 3. Admin = AP clerk + finance manager + controller (+ read-only auditor). */
    static Map<String, Object> userRow(Map<String, String> row) {
        Map<String, Object> out = rename(row, "user_id", "id");
        String sourceRole = row.get("role");
        out.put("source_role", sourceRole);
        out.put("role", switch (sourceRole) {
            case "employee" -> "EMPLOYEE";
            case "approver" -> "APPROVER";
            case "ap_clerk", "finance_manager", "controller_admin", "auditor" -> "ADMIN";
            default -> throw new IllegalArgumentException("Unknown role in users.csv: " + sourceRole);
        });
        out.put("read_only", "auditor".equals(sourceRole));
        // Release limits from approval_policies.csv: P09 finance manager up to $100k, P10 controller above.
        // AP clerks prepare but cannot release, so their limit is 0.
        out.put("release_limit_cad", switch (sourceRole) {
            case "controller_admin" -> row.get("approval_limit_cad");
            case "finance_manager" -> "100000";
            default -> "0";
        });
        return out;
    }

    static Map<String, Object> bankChangeRow(Map<String, String> row) {
        Map<String, Object> out = rename(row, "change_id", "id");
        out.put("status", "yes".equals(row.get("verified_by_callback")) ? "VERIFIED" : "PENDING");
        return out;
    }

    /** invoices.csv statuses are lower-case; ours are upper-case. The original is kept in source_status. */
    private Map<String, Object> invoiceRow(Map<String, String> row, Map<String, String> firstInvoiceByNumber,
                                           List<String[]> repeats) {
        Map<String, Object> out = rename(row, "invoice_id", "id");
        out.put("source_status", row.get("status"));
        out.put("status", switch (row.get("status")) {
            case "pending_approval" -> "PENDING_APPROVAL";
            case "approved" -> "APPROVED";
            case "scheduled" -> "SCHEDULED";
            case "paid" -> "PAID";
            case "rejected" -> "REJECTED";
            default -> throw new IllegalArgumentException("Unknown invoice status: " + row.get("status"));
        });
        out.put("created_at", row.get("received_date"));

        // The history contains repeated (vendor, invoice number) pairs. The unique constraint would refuse them,
        // so the later copy is loaded WITHOUT a canonical number and gets a visible flag. Nothing is dropped.
        String canonical = InvoiceNumbers.canonical(row.get("vendor_invoice_number"));
        String key = row.get("vendor_id") + "|" + canonical;
        String first = firstInvoiceByNumber.putIfAbsent(key, row.get("invoice_id"));
        if (first == null) {
            out.put("canonical_invoice_number", canonical);
        } else {
            out.put("canonical_invoice_number", null);
            repeats.add(new String[]{row.get("invoice_id"), first, row.get("vendor_invoice_number")});
        }
        return out;
    }

    private void flagHistoricalRepeats(List<String[]> repeats) {
        for (String[] r : repeats) {
            log.warn("Historical repeat: {} has the same vendor and invoice number ({}) as {}", r[0], r[2], r[1]);
            jdbc.update("INSERT INTO flags (invoice_id, node, code, severity, message) VALUES (?, 'SEED', ?, 'FLAG', ?)",
                    r[0], "HISTORICAL_REPEAT_NUMBER",
                    "Same vendor and invoice number " + r[2] + " as " + r[1] + " (possible double payment in history)");
        }
    }

    /** approvals.csv -> approval_steps. dept_manager is an APPROVER step; finance/controller/CFO are ADMIN steps. */
    static Map<String, Object> approvalRow(Map<String, String> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoice_id", row.get("invoice_id"));
        out.put("step_no", row.get("step_no"));
        out.put("required_role", "dept_manager".equals(row.get("approver_role")) ? "APPROVER" : "ADMIN");
        out.put("reason", "History: " + row.get("approver_role") + " (" + row.get("approval_id") + ")");
        out.put("assigned_user_id", row.get("approver_user_id"));
        out.put("status", switch (row.get("action")) {
            case "approved" -> "APPROVED";
            case "rejected" -> "REJECTED";
            default -> throw new IllegalArgumentException("Unknown approval action: " + row.get("action"));
        });
        out.put("acted_by", row.get("approver_user_id"));
        out.put("acted_at", row.get("acted_at"));
        out.put("comment", row.get("comment"));
        out.put("created_at", row.get("acted_at"));
        return out;
    }

    /** "outstanding" = cheque issued, not yet cashed, so it maps to SENT (on the rail, not settled). */
    static Map<String, Object> paymentRow(Map<String, String> row) {
        Map<String, Object> out = rename(row, "payment_id", "id");
        out.put("source_status", row.get("status"));
        out.put("status", switch (row.get("status")) {
            case "scheduled" -> "SCHEDULED";
            case "outstanding" -> "SENT";
            case "settled" -> "SETTLED";
            default -> throw new IllegalArgumentException("Unknown payment status: " + row.get("status"));
        });
        out.put("idempotency_key", row.get("payment_id"));
        return out;
    }
}
