package com.brim.easyinvoice.model;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A change to a vendor's bank details. Only an Admin other than the requester may verify it. */
@Entity
@Table(name = "bank_change_requests")
@Getter
@Setter
@NoArgsConstructor
public class BankChangeRequest {

    @Id
    private String id;
    private String vendorId;
    private LocalDate changedAt;
    private String changedByUserId;
    private String requestChannel;
    private boolean verifiedByCallback;
    private String verifiedByUserId;
    private String oldAccountLast4;
    private String newAccountLast4;
    private String status = "PENDING";   // PENDING | VERIFIED | REJECTED
}
