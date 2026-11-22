package com.brim.easyinvoice.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tax_codes")
@Getter
@Setter
@NoArgsConstructor
public class TaxCode {

    @Id
    private String code;
    private String description;
    @Column(precision = 8, scale = 5)
    private BigDecimal rate;
    private String appliesTo;
    private String recoverable;     // yes | no | n/a
    private String glAccount;
}
