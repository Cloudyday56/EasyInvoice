package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A row of approval_policies.csv, kept as written (the conditions are prose; routing applies them in code). */
@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    private String id;
    private String ruleType;        // approval | payment_release | match
    @Column(columnDefinition = "text")
    private String condition;
    @Column(columnDefinition = "text")
    private String required;
    @Column(columnDefinition = "text")
    private String notes;
}
