package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.BankChangeRequest;
import com.brim.easyinvoice.model.BankStatementLine;
import com.brim.easyinvoice.model.CardTransaction;
import com.brim.easyinvoice.model.Contract;
import com.brim.easyinvoice.model.Department;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.FlagSeverity;
import com.brim.easyinvoice.model.FxRate;
import com.brim.easyinvoice.model.GlAccount;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.InvoiceNumbers;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.Policy;
import com.brim.easyinvoice.model.PurchaseOrder;
import com.brim.easyinvoice.model.TaxCode;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.model.ApprovalStep;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Loads the sample dataset from data/data/*.csv into the tables.
 *
 * Each CSV row becomes a map (snake_case column name -> text), is converted to the table's entity by Jackson
 * (snake_case names match the entity fields, text becomes numbers/dates/enums), and is saved with JPA.
 * Jackson is strict: a CSV column the entity does not have fails the load instead of being dropped silently.
 *
 * Historical invoices are loaded with their final status; they do not go through the pipeline.
 */
@Component
@Transactional   // one transaction for the whole seed; DemoResetService's transaction is joined when resetting
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final int FLUSH_EVERY = 500;

    private final ObjectMapper csvMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final EntityManager entityManager;
    private final AuditService audit;
    private final Path csvDir;

    public SeedLoader(EntityManager entityManager, AuditService audit,
                      @Value("${app.data-dir}") String dataDir) {
        this.entityManager = entityManager;
        this.audit = audit;
        this.csvDir = Path.of(dataDir).resolve("data");
    }

    public void seedAll() {
        log.info("Seeding from {}", csvDir.toAbsolutePath());
        Map<String, Object> counts = new LinkedHashMap<>();

        // Foreign keys fix the order: a table is loaded after every table it points at.
        // Departments and users point at each other (and users at users), so those links are filled in afterwards.
        Map<String, String> departmentManagers = new HashMap<>();
        Map<String, String[]> userManagersAndDelegates = new HashMap<>();

        counts.put("departments", load("departments.csv", Department.class, row -> rename(row, "department_id", "id"),
                List.of("manager_user_id"), (d, refs) -> departmentManagers.put(d.getId(), (String) refs.get("manager_user_id"))));
        counts.put("users", load("users.csv", User.class, SeedLoader::userRow,
                List.of("department_id", "manager_id", "delegate_user_id"), (u, refs) -> {
                    u.setDepartment(ref(Department.class, refs.get("department_id")));
                    userManagersAndDelegates.put(u.getId(),
                            new String[]{(String) refs.get("manager_id"), (String) refs.get("delegate_user_id")});
                }));
        linkDepartmentManagers(departmentManagers);
        linkUserManagersAndDelegates(userManagersAndDelegates);

        counts.put("gl_accounts", load("gl_accounts.csv", GlAccount.class, SeedLoader::asRow));
        counts.put("vendors", load("vendors.csv", Vendor.class, SeedLoader::vendorRow));
        counts.put("bank_change_requests", load("bank_change_history.csv", BankChangeRequest.class,
                SeedLoader::bankChangeRow, List.of("vendor_id", "changed_by_user_id"), (r, refs) -> {
                    r.setVendor(ref(Vendor.class, refs.get("vendor_id")));
                    r.setChangedBy(ref(User.class, refs.get("changed_by_user_id")));
                }));
        counts.put("purchase_orders", load("purchase_orders.csv", PurchaseOrder.class,
                row -> rename(row, "po_id", "id"),
                List.of("vendor_id", "department_id", "requester_user_id", "approved_by"), (po, refs) -> {
                    po.setVendor(ref(Vendor.class, refs.get("vendor_id")));
                    po.setDepartment(ref(Department.class, refs.get("department_id")));
                    po.setRequester(ref(User.class, refs.get("requester_user_id")));
                    po.setApprovedBy(ref(User.class, refs.get("approved_by")));
                }));
        counts.put("contracts", load("contracts.csv", Contract.class, SeedLoader::contractRow,
                List.of("vendor_id"), (c, refs) -> c.setVendor(ref(Vendor.class, refs.get("vendor_id")))));
        counts.put("tax_codes", load("tax_codes.csv", TaxCode.class, SeedLoader::asRow));
        counts.put("fx_rates", load("fx_rates.csv", FxRate.class, row -> rename(row, "date", "rate_date")));
        counts.put("policies", persistAll(Policy.class, PolicySeeds.rows(), List.of(), null));

        Map<String, String> firstInvoiceByNumber = new HashMap<>();
        List<String[]> repeats = new ArrayList<>();
        counts.put("invoices", load("invoices.csv", Invoice.class, row -> invoiceRow(row, firstInvoiceByNumber, repeats),
                List.of("vendor_id", "department_id", "requester_user_id", "po_id", "contract_id"), (i, refs) -> {
                    i.setVendor(ref(Vendor.class, refs.get("vendor_id")));
                    i.setDepartment(ref(Department.class, refs.get("department_id")));
                    i.setRequester(ref(User.class, refs.get("requester_user_id")));
                    i.setPoNumberRaw((String) refs.get("po_id"));
                    i.setPurchaseOrder(ref(PurchaseOrder.class, refs.get("po_id")));
                    i.setContract(ref(Contract.class, refs.get("contract_id")));
                }));
        flagHistoricalRepeats(repeats);

        counts.put("invoice_lines", load("invoice_lines.csv", InvoiceLine.class, SeedLoader::asRow,
                List.of("invoice_id", "department_id", "gl_account"), (l, refs) -> {
                    l.setInvoice(ref(Invoice.class, refs.get("invoice_id")));
                    l.setDepartment(ref(Department.class, refs.get("department_id")));
                    l.setGlAccount(ref(GlAccount.class, refs.get("gl_account")));
                }));
        counts.put("approval_steps", load("approvals.csv", ApprovalStep.class, SeedLoader::approvalRow,
                List.of("invoice_id", "assigned_user_id", "acted_by"), (st, refs) -> {
                    st.setInvoice(ref(Invoice.class, refs.get("invoice_id")));
                    st.setAssignedUser(ref(User.class, refs.get("assigned_user_id")));
                    st.setActedBy(ref(User.class, refs.get("acted_by")));
                }));
        counts.put("payments", load("payments.csv", Payment.class, SeedLoader::paymentRow,
                List.of("invoice_id", "vendor_id", "released_by"), (pay, refs) -> {
                    pay.setInvoice(ref(Invoice.class, refs.get("invoice_id")));
                    pay.setVendor(ref(Vendor.class, refs.get("vendor_id")));
                    pay.setReleasedBy(ref(User.class, refs.get("released_by")));
                }));
        counts.put("card_transactions", load("card_transactions.csv", CardTransaction.class,
                SeedLoader::cardTransactionRow, List.of("cardholder_user_id"),
                (t, refs) -> t.setCardholder(ref(User.class, refs.get("cardholder_user_id")))));
        counts.put("bank_statement_lines", load("bank_statement.csv", BankStatementLine.class, row -> rename(row, "bank_txn_id", "id")));

        // TODO: inbox/emails.json and incoming_invoices/ are NOT seeded on purpose:
        //       they are unprocessed mail. An Admin pulls them in with "Poll inbox" (POST /api/admin/poll-inbox).

        log.info("Seed complete: {}", counts);
        audit.system(null, "seed.loaded", null, null, null, counts);
    }

    // ---------- generic loader ----------

    /** A table with no foreign keys. */
    private <T> int load(String fileName, Class<T> entityType,
                         Function<Map<String, String>, Map<String, Object>> toRow) {
        return load(fileName, entityType, toRow, List.of(), null);
    }

    /**
     * Reads one CSV, converts each row with toRow, and saves it as the given entity.
     * The foreign-key columns (relationColumns) are taken out of the row before Jackson converts it, and handed to
     * the linker, which sets the entity's relationships. Returns the row count.
     */
    private <T> int load(String fileName, Class<T> entityType,
                         Function<Map<String, String>, Map<String, Object>> toRow,
                         List<String> relationColumns, BiConsumer<T, Map<String, Object>> linker) {
        Path file = csvDir.resolve(fileName);
        if (!Files.exists(file)) {
            log.warn("TODO seed: {} not found, {} left empty", file, entityType.getSimpleName());
            return 0;
        }
        return persistAll(entityType, CsvReader.read(file).stream().map(toRow).toList(), relationColumns, linker);
    }

    private <T> int persistAll(Class<T> entityType, List<Map<String, Object>> rows,
                               List<String> relationColumns, BiConsumer<T, Map<String, Object>> linker) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            row.replaceAll((column, value) -> emptyToNull(value));
            Map<String, Object> refs = new HashMap<>();
            for (String column : relationColumns) {
                refs.put(column, row.remove(column));
            }
            T entity = csvMapper.convertValue(row, entityType);
            if (linker != null) linker.accept(entity, refs);
            entityManager.persist(entity);
            if (++count % FLUSH_EVERY == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        return count;
    }

    /** A reference to the row with this id (no query); null when the CSV cell was empty. */
    private <E> E ref(Class<E> type, Object id) {
        return id == null ? null : entityManager.getReference(type, id);
    }

    private void linkDepartmentManagers(Map<String, String> managerByDepartment) {
        managerByDepartment.forEach((departmentId, managerId) -> {
            if (managerId != null) {
                entityManager.find(Department.class, departmentId).setManager(ref(User.class, managerId));
            }
        });
        entityManager.flush();
        entityManager.clear();
    }

    private void linkUserManagersAndDelegates(Map<String, String[]> byUser) {
        byUser.forEach((userId, managerAndDelegate) -> {
            User user = entityManager.find(User.class, userId);
            user.setManager(ref(User.class, managerAndDelegate[0]));
            user.setDelegate(ref(User.class, managerAndDelegate[1]));
        });
        entityManager.flush();
        entityManager.clear();
    }

    private static Object emptyToNull(Object value) {
        return (value instanceof String s && s.isEmpty()) ? null : value;
    }

    private static Map<String, Object> asRow(Map<String, String> row) {
        return new LinkedHashMap<>(row);
    }

    private static Map<String, Object> rename(Map<String, String> row, String from, String to) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(key.equals(from) ? to : key, value));
        return out;
    }

    /** The CSVs write booleans as yes/no. Anything else is a data problem, so it fails loudly. */
    private static Map<String, Object> yesNoToBoolean(Map<String, Object> row, String... columns) {
        for (String column : columns) {
            Object value = row.get(column);
            if (value instanceof String s && !s.isEmpty()) {
                if (!s.equals("yes") && !s.equals("no")) {
                    throw new IllegalArgumentException("Expected yes/no in column " + column + " but found: " + s);
                }
                row.put(column, s.equals("yes"));
            }
        }
        return row;
    }

    /** Dates become midnight UTC; local date-times ("2026-01-03T15:59:00") are taken as UTC. */
    private static String atUtc(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.length() == 10 ? value + "T00:00:00Z" : value + "Z";
    }

    // ---------- per-table conversions ----------

    /**
     * users.csv has 6 roles; the app has 3. AP clerks and the auditor become Employees (they request invoices
     * and have no approval or release authority); finance manager and controller become Admins.
     */
    static Map<String, Object> userRow(Map<String, String> row) {
        Map<String, Object> out = rename(row, "user_id", "id");
        String sourceRole = row.get("role");
        out.put("source_role", sourceRole);
        out.put("role", switch (sourceRole) {
            case "approver" -> "APPROVER";
            case "finance_manager", "controller_admin" -> "ADMIN";
            case "employee", "ap_clerk", "auditor" -> "EMPLOYEE";
            default -> throw new IllegalArgumentException("Unknown role in users.csv: " + sourceRole);
        });
        return out;
    }

    static Map<String, Object> vendorRow(Map<String, String> row) {
        return yesNoToBoolean(rename(row, "vendor_id", "id"), "accepts_card_payment", "requires_po");
    }

    static Map<String, Object> contractRow(Map<String, String> row) {
        return yesNoToBoolean(rename(row, "contract_id", "id"), "auto_renew");
    }

    static Map<String, Object> bankChangeRow(Map<String, String> row) {
        Map<String, Object> out = rename(row, "change_id", "id");
        out.put("status", "yes".equals(row.get("verified_by_callback")) ? "VERIFIED" : "PENDING");
        return yesNoToBoolean(out, "verified_by_callback");
    }

    static Map<String, Object> cardTransactionRow(Map<String, String> row) {
        return yesNoToBoolean(rename(row, "txn_id", "id"), "receipt_attached");
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
        out.put("created_at", atUtc(row.get("received_date")));

        // The history contains repeated (vendor, invoice number) pairs. The unique constraint would refuse them,
        // so the later copy is loaded WITHOUT a canonical number and gets a visible flag. Nothing is dropped.
        String canonical = InvoiceNumbers.canonical(row.get("vendor_invoice_number"));
        String key = row.get("vendor_id") + "|" + canonical;
        String first = firstInvoiceByNumber.putIfAbsent(key, row.get("invoice_id"));
        if (first == null) {
            out.put("canonical_invoice_number", canonical);
        } else {
            repeats.add(new String[]{row.get("invoice_id"), first, row.get("vendor_invoice_number")});
        }
        return out;
    }

    private void flagHistoricalRepeats(List<String[]> repeats) {
        for (String[] r : repeats) {
            log.warn("Historical repeat: {} has the same vendor and invoice number ({}) as {}", r[0], r[2], r[1]);
            entityManager.persist(new Flag(ref(Invoice.class, r[0]), "SEED", "HISTORICAL_REPEAT_NUMBER", FlagSeverity.FLAG,
                    "Same vendor and invoice number " + r[2] + " as " + r[1] + " (possible double payment in history)"));
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
        out.put("acted_at", atUtc(row.get("acted_at")));
        out.put("comment", row.get("comment"));
        out.put("created_at", atUtc(row.get("acted_at")));
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
