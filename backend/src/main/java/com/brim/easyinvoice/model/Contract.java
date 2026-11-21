package com.brim.easyinvoice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract {

    @Id
    private String id;
    private String vendorId;
    private String description;
    private String billingType;
    @Column(precision = 14, scale = 2)
    private BigDecimal monthlyFee;
    private String currency;
    @Column(precision = 14, scale = 2)
    private BigDecimal hourlyRateCap;
    @Column(precision = 6, scale = 2)
    private BigDecimal fuelSurchargeCapPct;
    @Column(precision = 14, scale = 2)
    private BigDecimal notToExceedMonthly;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoRenew;
    private String status;
}
