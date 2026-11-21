package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity<String> {

    @Id
    private String id;
    private String fullName;
    private String email;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegate_user_id")
    private User delegate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    private String sourceRole;      // role as written in users.csv (ap_clerk, auditor...)
    private boolean readOnly;       // auditor: an Admin who can only look
    @Column(precision = 14, scale = 2)
    private BigDecimal approvalLimitCad;
    @Column(precision = 14, scale = 2)
    private BigDecimal releaseLimitCad;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    private String workProvince;
    private LocalDate statusSince;
    private LocalDate hireDate;

    public boolean isActive() {
        return status != UserStatus.TERMINATED;
    }

    public boolean isOnLeave() {
        return status == UserStatus.ON_LEAVE;
    }

    // Id shortcuts: read the foreign key without loading the related row (safe on a lazy reference).

    public String getDepartmentId() {
        return department == null ? null : department.getId();
    }

    public String getManagerId() {
        return manager == null ? null : manager.getId();
    }

    public String getDelegateId() {
        return delegate == null ? null : delegate.getId();
    }
}
