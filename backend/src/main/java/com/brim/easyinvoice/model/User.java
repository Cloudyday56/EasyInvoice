package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private String id;
    private String fullName;
    private String email;
    private String title;
    private String departmentId;
    private String managerId;
    @Enumerated(EnumType.STRING)
    private Role role;
    private String sourceRole;      // role as written in users.csv (ap_clerk, auditor...)
    private boolean readOnly;       // auditor: an Admin who can only look
    private BigDecimal approvalLimitCad;
    private BigDecimal releaseLimitCad;
    private String status;          // active | on_leave | terminated
    private String delegateUserId;

    public boolean isActive() {
        return !"terminated".equals(status);
    }

    public boolean isOnLeave() {
        return "on_leave".equals(status);
    }
}
