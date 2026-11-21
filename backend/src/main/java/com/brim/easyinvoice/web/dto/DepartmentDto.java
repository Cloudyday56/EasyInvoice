package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Department;

import java.math.BigDecimal;

public record DepartmentDto(String id, String name, String costCentre, BigDecimal annualBudgetCad,
                            String managerUserId) {

    public static DepartmentDto from(Department d) {
        return new DepartmentDto(d.getId(), d.getName(), d.getCostCentre(), d.getAnnualBudgetCad(),
                d.getManager() == null ? null : d.getManager().getId());
    }
}
