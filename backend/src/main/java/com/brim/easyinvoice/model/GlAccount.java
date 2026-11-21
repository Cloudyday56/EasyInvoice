package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gl_accounts")
@Getter
@Setter
@NoArgsConstructor
public class GlAccount {

    @Id
    private String accountNumber;
    private String name;
    private String accountType;
    private String notes;
}
