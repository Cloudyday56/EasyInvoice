package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.ApprovalStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ApprovalStepRepository {

    private static final RowMapper<ApprovalStep> MAPPER = (rs, i) -> new ApprovalStep(
            rs.getLong("id"),
            rs.getString("invoice_id"),
            rs.getInt("step_no"),
            rs.getString("required_role"),
            rs.getString("reason"),
            rs.getString("assigned_user_id"),
            rs.getString("acting_for"),
            rs.getString("status"),
            rs.getString("acted_by"),
            rs.getObject("acted_at", OffsetDateTime.class),
            rs.getString("comment"));

    private final JdbcTemplate jdbc;

    public ApprovalStepRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ApprovalStep> forInvoice(String invoiceId) {
        return jdbc.query("SELECT * FROM approval_steps WHERE invoice_id = ? ORDER BY step_no, id", MAPPER, invoiceId);
    }

    /** All steps grouped by invoice: one query for list screens instead of one per invoice. */
    public Map<String, List<ApprovalStep>> allGroupedByInvoice() {
        return jdbc.query("SELECT * FROM approval_steps ORDER BY step_no, id", MAPPER).stream()
                .collect(Collectors.groupingBy(ApprovalStep::invoiceId));
    }

    public List<ApprovalStep> pendingAssignedTo(String userId) {
        return jdbc.query("SELECT * FROM approval_steps WHERE assigned_user_id = ? AND status = 'PENDING' "
                + "ORDER BY created_at", MAPPER, userId);
    }

    public void insert(String invoiceId, int stepNo, String requiredRole, String reason,
                       String assignedUserId, String actingFor) {
        jdbc.update("""
                INSERT INTO approval_steps (invoice_id, step_no, required_role, reason, assigned_user_id, acting_for)
                VALUES (?, ?, ?, ?, ?, ?)
                """, invoiceId, stepNo, requiredRole, reason, assignedUserId, actingFor);
    }

    public void recordDecision(long stepId, String status, String actedBy, String comment) {
        jdbc.update("UPDATE approval_steps SET status = ?, acted_by = ?, acted_at = now(), comment = ? WHERE id = ?",
                status, actedBy, comment, stepId);
    }

    /** Voids every step still open or already approved, e.g. when the invoice goes back to AP. */
    public void voidAll(String invoiceId) {
        jdbc.update("UPDATE approval_steps SET status = 'VOID' WHERE invoice_id = ? AND status IN ('PENDING','APPROVED')",
                invoiceId);
    }
}
