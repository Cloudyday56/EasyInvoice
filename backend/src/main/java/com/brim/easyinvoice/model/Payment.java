package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** status: SCHEDULED -> RELEASED -> SENT -> SETTLED, or FAILED. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    private String id;
    private String invoiceId;
    private String vendorId;
    private String method;            // EFT | CHEQUE | CARD
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
    private String status;
    private String sourceStatus;      // status as written in payments.csv (history only)
    @Column(unique = true)
    private String idempotencyKey;    // = payment id; a retry can never pay twice
    private String preparedBy;
    private String releasedBy;
    private OffsetDateTime releasedAt;
    private OffsetDateTime sentAt;
    @Column(columnDefinition = "text")
    private String failureReason;
}
