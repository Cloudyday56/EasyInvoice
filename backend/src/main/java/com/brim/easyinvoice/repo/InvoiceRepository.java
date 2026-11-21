package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class InvoiceRepository {

    /** Columns a node is allowed to change through StageResult.patch. Status is NOT here on purpose. */
    public static final Set<String> PATCHABLE_COLUMNS = Set.of(
            "document_type", "vendor_id", "vendor_name_raw", "vendor_invoice_number", "canonical_invoice_number",
            "invoice_date", "due_date", "currency", "subtotal", "gst_hst_amount", "pst_qst_amount", "total",
            "fx_rate_to_cad", "total_cad", "department_id", "po_id", "contract_id", "ship_to_province", "risk_score");

    private static final RowMapper<Invoice> MAPPER = (rs, i) -> new Invoice(
            rs.getString("id"),
            (Long) rs.getObject("source_document_id"),
            rs.getString("document_type"),
            rs.getString("vendor_id"),
            rs.getString("vendor_name_raw"),
            rs.getString("vendor_invoice_number"),
            rs.getString("canonical_invoice_number"),
            rs.getObject("invoice_date", LocalDate.class),
            rs.getObject("received_date", LocalDate.class),
            rs.getObject("due_date", LocalDate.class),
            rs.getString("currency"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("gst_hst_amount"),
            rs.getBigDecimal("pst_qst_amount"),
            rs.getBigDecimal("total"),
            rs.getBigDecimal("fx_rate_to_cad"),
            rs.getBigDecimal("total_cad"),
            rs.getString("department_id"),
            rs.getString("requester_user_id"),
            rs.getString("po_id"),
            rs.getString("contract_id"),
            rs.getString("ship_to_province"),
            (Integer) rs.getObject("risk_score"),
            InvoiceStatus.valueOf(rs.getString("status")),
            rs.getString("hold_queue"),
            rs.getString("hold_reason"),
            rs.getString("resume_node"),
            rs.getString("current_node"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<InvoiceLine> LINE_MAPPER = (rs, i) -> new InvoiceLine(
            rs.getInt("line_no"),
            rs.getString("description"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("amount"),
            rs.getString("gl_account"),
            rs.getString("department_id"),
            rs.getString("gst_hst_code"),
            rs.getString("pst_qst_code"),
            rs.getString("line_type"));

    private static final RowMapper<Flag> FLAG_MAPPER = (rs, i) -> new Flag(
            rs.getLong("id"),
            rs.getString("invoice_id"),
            rs.getString("node"),
            rs.getString("code"),
            rs.getString("severity"),
            rs.getString("message"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getString("overridden_by"),
            rs.getString("override_reason"));

    private final JdbcTemplate jdbc;

    public InvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- reads ----------

    public Invoice get(String id) {
        return jdbc.query("SELECT * FROM invoices WHERE id = ?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
    }

    /** Newest first. Fine for a few hundred rows; would need paging at real volume. */
    public List<Invoice> findAll() {
        return jdbc.query("SELECT * FROM invoices ORDER BY created_at DESC, id DESC", MAPPER);
    }

    public List<Invoice> findOnHold() {
        return jdbc.query("SELECT * FROM invoices WHERE status IN ('ON_HOLD', 'ON_HOLD_VENDOR') "
                + "ORDER BY due_date NULLS LAST, created_at", MAPPER);
    }

    public List<InvoiceLine> lines(String invoiceId) {
        return jdbc.query("SELECT * FROM invoice_lines WHERE invoice_id = ? ORDER BY line_no", LINE_MAPPER, invoiceId);
    }

    public List<Flag> flags(String invoiceId) {
        return jdbc.query("SELECT * FROM flags WHERE invoice_id = ? ORDER BY id", FLAG_MAPPER, invoiceId);
    }

    /** Repeat guard: is there another invoice from this vendor with the same canonical number? */
    public boolean numberAlreadyUsed(String vendorId, String canonicalNumber, String exceptInvoiceId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM invoices
                WHERE vendor_id = ? AND canonical_invoice_number = ? AND id <> ?
                """, Integer.class, vendorId, canonicalNumber, exceptInvoiceId);
        return count != null && count > 0;
    }

    // ---------- writes ----------

    public String nextInvoiceId() {
        Long n = jdbc.queryForObject("SELECT nextval('new_invoice_seq')", Long.class);
        return String.format("EI-%05d", n);
    }

    public void insertDraft(String id, long sourceDocumentId, String requesterUserId, String departmentId) {
        jdbc.update("""
                INSERT INTO invoices (id, source_document_id, requester_user_id, department_id, received_date, status)
                VALUES (?, ?, ?, ?, current_date, 'RECEIVED')
                """, id, sourceDocumentId, requesterUserId, departmentId);
    }

    /** Only InvoiceStateMachine calls this. */
    public void updateStatus(String id, InvoiceStatus status, String holdQueue, String holdReason, String resumeNode) {
        jdbc.update("""
                UPDATE invoices SET status = ?, hold_queue = ?, hold_reason = ?, resume_node = ?, updated_at = now()
                WHERE id = ?
                """, status.name(), holdQueue, holdReason, resumeNode, id);
    }

    public void setCurrentNode(String id, String node) {
        jdbc.update("UPDATE invoices SET current_node = ?, updated_at = now() WHERE id = ?", node, id);
    }

    /** Applies a node's patch. Column names are checked against PATCHABLE_COLUMNS, so no SQL injection. */
    public void applyPatch(String id, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) return;
        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (!PATCHABLE_COLUMNS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Column not patchable: " + entry.getKey());
            }
            assignments.add(entry.getKey() + " = ?");
            values.add(entry.getValue());
        }
        values.add(id);
        jdbc.update("UPDATE invoices SET " + String.join(", ", assignments) + ", updated_at = now() WHERE id = ?",
                values.toArray());
    }

    public void replaceLines(String invoiceId, List<InvoiceLine> lines) {
        jdbc.update("DELETE FROM invoice_lines WHERE invoice_id = ?", invoiceId);
        for (InvoiceLine line : lines) {
            jdbc.update("""
                    INSERT INTO invoice_lines (invoice_id, line_no, description, quantity, unit_price, amount,
                        gl_account, department_id, gst_hst_code, pst_qst_code, line_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, invoiceId, line.lineNo(), line.description(), line.quantity(), line.unitPrice(),
                    line.amount(), line.glAccount(), line.departmentId(), line.gstHstCode(), line.pstQstCode(),
                    line.lineType());
        }
    }

    public void addFlag(String invoiceId, String node, String code, String severity, String message) {
        jdbc.update("INSERT INTO flags (invoice_id, node, code, severity, message) VALUES (?, ?, ?, ?, ?)",
                invoiceId, node, code, severity, message);
    }

    /** An Admin cleared the hold: blocking flags stay visible but are marked as overridden, with the reason. */
    public void overrideBlockFlags(String invoiceId, String userId, String reason) {
        jdbc.update("UPDATE flags SET overridden_by = ?, override_reason = ? "
                + "WHERE invoice_id = ? AND severity = 'BLOCK' AND overridden_by IS NULL", userId, reason, invoiceId);
    }

    public java.util.Optional<String> findIdBySourceDocument(long sourceDocumentId) {
        return jdbc.queryForList("SELECT id FROM invoices WHERE source_document_id = ?", String.class, sourceDocumentId)
                .stream().findFirst();
    }

    /** Re-running a node replaces its earlier flags (nodes are idempotent). */
    public void deleteFlagsFromNode(String invoiceId, String node) {
        jdbc.update("DELETE FROM flags WHERE invoice_id = ? AND node = ? AND overridden_by IS NULL", invoiceId, node);
    }
}
