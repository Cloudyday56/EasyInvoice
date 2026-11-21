package com.brim.easyinvoice.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Append-only audit log. The only code that inserts audit events. */
@Service
public class AuditService {

    /** Any constant works; it just names "the audit append lock" in Postgres. */
    private static final long APPEND_LOCK_ID = 4242;

    private final AuditEventRepository events;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditService(AuditEventRepository events, JdbcTemplate jdbc, ObjectMapper json) {
        this.events = events;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Event written by the pipeline (actor = system). */
    @Transactional
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

        String prevHash = events.findFirstByOrderByIdDesc().map(AuditEvent::getHash).orElse(AuditHasher.GENESIS);

        // Postgres stores microseconds, so truncate now; otherwise the re-read value would hash differently.
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String payloadJson = payload == null ? null : json.writeValueAsString(payload);

        AuditEvent draft = AuditEvent.builder()
                .invoiceId(invoiceId).paymentId(paymentId)
                .actorId(actor.id()).actingFor(actor.actingFor()).role(actor.role())
                .action(action).node(node).fromStatus(fromStatus).toStatus(toStatus)
                .payloadJson(payloadJson).createdAt(createdAt).prevHash(prevHash)
                .build();
        events.save(draft.toBuilder().hash(AuditHasher.hash(prevHash, draft)).build());
    }

    public List<AuditEvent> forInvoice(String invoiceId) {
        return events.findByInvoiceIdOrderById(invoiceId);
    }

    public List<AuditEvent> latest(int limit) {
        return events.findAllByOrderByIdDesc(PageRequest.ofSize(limit));
    }

    public List<AuditEvent> all() {
        return events.findAllByOrderById();
    }

    public AuditHasher.VerifyResult verifyChain() {
        return AuditHasher.verify(all());
    }

    public String exportCsv(String invoiceIdOrNull) {
        List<AuditEvent> rows = invoiceIdOrNull == null ? all() : forInvoice(invoiceIdOrNull);
        StringBuilder csv = new StringBuilder(
                "id,created_at,invoice_id,payment_id,actor_id,acting_for,role,action,node,from_status,to_status,payload_json,prev_hash,hash\n");
        for (AuditEvent e : rows) {
            csv.append(String.join(",", List.of(
                    String.valueOf(e.getId()), e.getCreatedAt().toString(), csvCell(e.getInvoiceId()),
                    csvCell(e.getPaymentId()), csvCell(e.getActorId()), csvCell(e.getActingFor()),
                    csvCell(e.getRole()), csvCell(e.getAction()), csvCell(e.getNode()),
                    csvCell(e.getFromStatus()), csvCell(e.getToStatus()), csvCell(e.getPayloadJson()),
                    e.getPrevHash(), e.getHash()))).append('\n');
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
