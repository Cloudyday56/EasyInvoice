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
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    private String id;
    private String vendorId;
    private String departmentId;
    private String requesterUserId;
    private LocalDate issueDate;
    private String description;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalAmount;
    private String currency;
    private String status;          // open | partially_invoiced | closed
    private String approvedBy;
}
