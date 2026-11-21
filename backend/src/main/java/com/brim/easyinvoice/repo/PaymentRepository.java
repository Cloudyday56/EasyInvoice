package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private static final RowMapper<Payment> MAPPER = (rs, i) -> new Payment(
            rs.getString("id"),
            rs.getString("invoice_id"),
            rs.getString("vendor_id"),
            rs.getString("method"),
            rs.getObject("scheduled_date", LocalDate.class),
            rs.getObject("paid_date", LocalDate.class),
            rs.getString("currency"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("amount_cad"),
            rs.getString("reference"),
            rs.getString("status"),
            rs.getString("prepared_by"),
            rs.getString("released_by"),
            rs.getObject("released_at", OffsetDateTime.class),
            rs.getObject("sent_at", OffsetDateTime.class),
            rs.getString("failure_reason"));

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Payment get(String id) {
        return jdbc.query("SELECT * FROM payments WHERE id = ?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Payment " + id + " not found"));
    }

    public List<Payment> findAll() {
        return jdbc.query("SELECT * FROM payments ORDER BY scheduled_date DESC NULLS LAST, id DESC", MAPPER);
    }

    /** The invoice's live payment: newest one that has not failed. */
    public Optional<Payment> activeForInvoice(String invoiceId) {
        return jdbc.query("SELECT * FROM payments WHERE invoice_id = ? AND status <> 'FAILED' ORDER BY id DESC LIMIT 1",
                MAPPER, invoiceId).stream().findFirst();
    }

    public List<Payment> forInvoice(String invoiceId) {
        return jdbc.query("SELECT * FROM payments WHERE invoice_id = ? ORDER BY id", MAPPER, invoiceId);
    }

    public String nextPaymentId() {
        Long n = jdbc.queryForObject("SELECT nextval('new_payment_seq')", Long.class);
        return String.format("EP-%05d", n);
    }

    public void insertScheduled(String id, String invoiceId, String vendorId, String method, LocalDate scheduledDate,
                                String currency, BigDecimal amount, BigDecimal amountCad) {
        jdbc.update("""
                INSERT INTO payments (id, invoice_id, vendor_id, method, scheduled_date, currency, amount,
                    amount_cad, status, idempotency_key, prepared_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED', ?, 'system')
                """, id, invoiceId, vendorId, method, scheduledDate, currency, amount, amountCad, id);
    }

    public void markReleased(String id, String releasedBy) {
        jdbc.update("UPDATE payments SET status = 'RELEASED', released_by = ?, released_at = now() WHERE id = ?",
                releasedBy, id);
    }

    public void markSent(String id, String reference) {
        jdbc.update("UPDATE payments SET status = 'SENT', reference = ?, sent_at = now() WHERE id = ?", reference, id);
    }

    public void markSettled(String id) {
        jdbc.update("UPDATE payments SET status = 'SETTLED', paid_date = current_date WHERE id = ?", id);
    }

    public void markFailed(String id, String reason) {
        jdbc.update("UPDATE payments SET status = 'FAILED', failure_reason = ? WHERE id = ?", reason, id);
    }

    public void issueVirtualCard(String paymentId, String maskedNumber, BigDecimal amountLimit, String vendorId) {
        jdbc.update("""
                INSERT INTO virtual_cards (payment_id, masked_number, amount_limit, merchant_lock, expires_at)
                VALUES (?, ?, ?, ?, now() + interval '7 days')
                """, paymentId, maskedNumber, amountLimit, vendorId);
    }
}
