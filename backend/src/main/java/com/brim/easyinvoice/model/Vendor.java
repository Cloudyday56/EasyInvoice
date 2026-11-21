package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Only the vendor columns the app reads so far; the table has more (bank details, tax numbers...). */
@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
public class Vendor {

    @Id
    private String id;
    private String legalName;
    private String category;
    private String defaultGlAccount;
    private String status;            // active | pending_review | on_hold | blocked
    private boolean acceptsCardPayment;
    private Integer paymentTermsDays;
    private String apContactEmail;

    public boolean isActive() {
        return "active".equals(status);
    }
}
