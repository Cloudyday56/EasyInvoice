package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** severity is FLAG (warning that travels with the invoice) or BLOCK (stopped the line). */
@Entity
@Table(name = "flags", indexes = @Index(name = "idx_flags_invoice", columnList = "invoice_id"))
@Getter
@Setter
@NoArgsConstructor
public class Flag extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    private String node;
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlagSeverity severity;
    @Column(columnDefinition = "text")
    private String message;
    @org.hibernate.annotations.CreationTimestamp
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overridden_by")
    private User overriddenBy;
    private String overrideReason;

    public Flag(Invoice invoice, String node, String code, FlagSeverity severity, String message) {
        this.invoice = invoice;
        this.node = node;
        this.code = code;
        this.severity = severity;
        this.message = message;
    }

    public boolean isOverridden() {
        return overriddenBy != null;
    }

    // Id shortcuts: read the foreign key without loading the related row (safe on a lazy reference).

    public String getInvoiceId() {
        return invoice == null ? null : invoice.getId();
    }

    public String getOverriddenById() {
        return overriddenBy == null ? null : overriddenBy.getId();
    }
}
