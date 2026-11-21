package com.brim.easyinvoice.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An approval rule as JSON, evaluated in sortOrder by the Approval routing node. */
@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    private String id;
    private String ruleType;        // approval | payment_release | match
    @JdbcTypeCode(SqlTypes.JSON)
    private String ruleJson;
    @Column(columnDefinition = "text")
    private String sourceText;      // the original condition from approval_policies.csv
    private boolean enabled = true;
    private int sortOrder;
}
