package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceId;
    private int stepNo;
    private String requiredRole;     // APPROVER | ADMIN
    private String reason;           // why this step exists (policy, flag, escalation)
    private String assignedUserId;
    private String actingFor;        // original approver, when assignedUserId is a delegate
    private String status = "PENDING";  // PENDING | APPROVED | REJECTED | RETURNED | VOID
    private String actedBy;
    private OffsetDateTime actedAt;
    private String comment;
    @CreationTimestamp
    private OffsetDateTime createdAt;

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
}
