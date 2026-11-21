package com.brim.easyinvoice.model;

import java.math.BigDecimal;

public record User(
        String id,
        String fullName,
        String email,
        String title,
        String departmentId,
        String managerId,
        Role role,
        String sourceRole,
        boolean readOnly,
        BigDecimal approvalLimitCad,
        BigDecimal releaseLimitCad,
        String status,          // active | on_leave | terminated
        String delegateUserId) {

    public boolean isActive() {
        return !"terminated".equals(status);
    }

    public boolean isOnLeave() {
        return "on_leave".equals(status);
    }
}
