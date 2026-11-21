package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** status: SCHEDULED -> RELEASED -> SENT -> SETTLED, or FAILED. */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_invoice", columnList = "invoice_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity<String> {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;
    private LocalDate scheduledDate;
    private LocalDate paidDate;
    private String currency;
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(precision = 10, scale = 4)
    private BigDecimal fxRateToCad;
    @Column(precision = 14, scale = 2)
    private BigDecimal amountCad;
    @Column(precision = 14, scale = 2)
    private BigDecimal discountTaken;
    private String reference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    private String sourceStatus;      // status as written in payments.csv (history only)
    @Column(unique = true)
    private String idempotencyKey;    // = payment id; a retry can never pay twice
    private String preparedBy;        // a user id, or "system" when the pipeline scheduled it
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by")
    private User releasedBy;
    private OffsetDateTime releasedAt;
    private OffsetDateTime sentAt;
    @Column(columnDefinition = "text")
    private String failureReason;

    // Id shortcuts: read the foreign key without loading the related row (safe on a lazy reference).

    public String getInvoiceId() {
        return invoice == null ? null : invoice.getId();
    }

    public String getVendorId() {
        return vendor == null ? null : vendor.getId();
    }

    public String getReleasedById() {
        return releasedBy == null ? null : releasedBy.getId();
    }
}
