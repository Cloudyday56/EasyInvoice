package com.brim.easyinvoice.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Append-only audit log. The only code that inserts into audit_events. */
@Service
public class AuditService {

    /** Any constant works; it just names "the audit append lock" in Postgres. */
    private static final long APPEND_LOCK_ID = 4242;

    private static final RowMapper<AuditEvent> MAPPER = (rs, i) -> new AuditEvent(
            rs.getLong("id"),
            rs.getString("invoice_id"),
            rs.getString("payment_id"),
            rs.getString("actor_id"),
            rs.getString("acting_for"),
            rs.getString("role"),
            rs.getString("action"),
            rs.getString("node"),
            rs.getString("from_status"),
            rs.getString("to_status"),
            rs.getString("payload_json"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getString("prev_hash"),
            rs.getString("hash"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Event written by the pipeline (actor = system). */
    public void system(String invoiceId, String action, String node, String fromStatus, String toStatus,
                       Map<String, ?> payload) {
        record(Actor.SYSTEM, invoiceId, null, action, node, fromStatus, toStatus, payload);
    }

    /**
     * Appends one event. The advisory lock makes appends take turns until the surrounding
     * transaction commits, so two writers can never both chain onto the same prev_hash.
     */
    @Transactional
    public void record(Actor actor, String invoiceId, String paymentId, String action, String node,
                       String fromStatus, String toStatus, Map<String, ?> payload) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, APPEND_LOCK_ID);

        String prevHash = jdbc.queryForList("SELECT hash FROM audit_events ORDER BY id DESC LIMIT 1", String.class)
                .stream().findFirst().orElse(AuditHasher.GENESIS);

        // Postgres stores microseconds, so truncate now; otherwise the re-read value would hash differently.
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String payloadJson = payload == null ? null : json.writeValueAsString(payload);

        AuditEvent draft = new AuditEvent(null, invoiceId, paymentId, actor.id(), actor.actingFor(), actor.role(),
                action, node, fromStatus, toStatus, payloadJson, createdAt, prevHash, null);
        String hash = AuditHasher.hash(prevHash, draft);

        jdbc.update("""
                INSERT INTO audit_events (invoice_id, payment_id, actor_id, acting_for, role, action, node,
                    from_status, to_status, payload_json, created_at, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, invoiceId, paymentId, actor.id(), actor.actingFor(), actor.role(), action, node,
                fromStatus, toStatus, payloadJson, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), prevHash, hash);
    }

    public List<AuditEvent> forInvoice(String invoiceId) {
        return jdbc.query("SELECT * FROM audit_events WHERE invoice_id = ? ORDER BY id", MAPPER, invoiceId);
    }

    public List<AuditEvent> latest(int limit) {
        return jdbc.query("SELECT * FROM audit_events ORDER BY id DESC LIMIT ?", MAPPER, limit);
    }

    public List<AuditEvent> all() {
        return jdbc.query("SELECT * FROM audit_events ORDER BY id", MAPPER);
    }

    public AuditHasher.VerifyResult verifyChain() {
        return AuditHasher.verify(all());
    }

    public String exportCsv(String invoiceIdOrNull) {
        List<AuditEvent> events = invoiceIdOrNull == null ? all() : forInvoice(invoiceIdOrNull);
        StringBuilder csv = new StringBuilder(
                "id,created_at,invoice_id,payment_id,actor_id,acting_for,role,action,node,from_status,to_status,payload_json,prev_hash,hash\n");
        for (AuditEvent e : events) {
            csv.append(String.join(",", List.of(
                    String.valueOf(e.id()), e.createdAt().toString(), csvCell(e.invoiceId()), csvCell(e.paymentId()),
                    csvCell(e.actorId()), csvCell(e.actingFor()), csvCell(e.role()), csvCell(e.action()),
                    csvCell(e.node()), csvCell(e.fromStatus()), csvCell(e.toStatus()), csvCell(e.payloadJson()),
                    e.prevHash(), e.hash()))).append('\n');
        }
        return csv.toString();
    }

    private static String csvCell(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
