package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department extends BaseEntity<String> {

    @Id
    private String id;
    private String name;
    private String costCentre;
    @Column(precision = 14, scale = 2)
    private BigDecimal annualBudgetCad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id")
    private User manager;
}
