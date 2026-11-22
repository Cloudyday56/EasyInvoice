package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fx_rates")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

    @Id
    @Column(name = "date")
    private LocalDate rateDate;
    @Column(precision = 10, scale = 4)
    private BigDecimal usdToCad;
}
