package com.brim.easyinvoice.audit;

import java.time.Instant;

/** One immutable row of audit_events. id is null until the row is inserted. */
public record AuditEvent(
        Long id,
        String invoiceId,
        String paymentId,
        String actorId,
        String actingFor,
        String role,
        String action,
        String node,
        String fromStatus,
        String toStatus,
        String payloadJson,
        Instant createdAt,
        String prevHash,
        String hash) {
}
