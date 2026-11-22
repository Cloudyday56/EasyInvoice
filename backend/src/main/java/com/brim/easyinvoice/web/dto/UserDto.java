package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserDto(
        String id,
        String fullName,
        String email,
        String title,
        String departmentId,
        String managerId,
        Role role,
        String sourceRole,
        BigDecimal approvalLimitCad,
        UserStatus status,
        String delegateUserId,
        String workProvince,
        LocalDate statusSince,
        LocalDate hireDate,
        boolean active,
        boolean onLeave) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getTitle(), u.getDepartmentId(),
                u.getManagerId(), u.getRole(), u.getSourceRole(), u.getApprovalLimitCad(),
                u.getStatus(), u.getDelegateId(), u.getWorkProvince(), u.getStatusSince(),
                u.getHireDate(), u.isActive(), u.isOnLeave());
    }
}
