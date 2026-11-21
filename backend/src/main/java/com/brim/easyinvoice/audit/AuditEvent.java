package com.brim.easyinvoice.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One immutable row of audit_events. id is null until the row is inserted. */
@Entity
@Immutable   // Hibernate never issues an UPDATE for it (and the database trigger would refuse one anyway)
@Table(name = "audit_events")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceId;
    private String paymentId;
    private String actorId;       // user id, or "system" for the pipeline
    private String actingFor;
    private String role;
    private String action;
    private String node;
    private String fromStatus;
    private String toStatus;
    private String payloadJson;
    private Instant createdAt;
    private String prevHash;
    private String hash;
}
