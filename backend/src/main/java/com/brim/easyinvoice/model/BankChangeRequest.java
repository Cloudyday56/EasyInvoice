package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A change to a vendor's bank details. Only an Admin other than the requester may verify it. */
@Entity
@Table(name = "bank_change_requests")
@Getter
@Setter
@NoArgsConstructor
public class BankChangeRequest extends BaseEntity<String> {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    private LocalDate changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    private String requestChannel;
    private boolean verifiedByCallback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    private String oldAccountLast4;
    private String newAccountLast4;
    private String status = "PENDING";   // PENDING | VERIFIED | REJECTED
}
