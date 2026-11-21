package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends BaseEntity<String> {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

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
