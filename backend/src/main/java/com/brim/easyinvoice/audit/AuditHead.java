package com.brim.easyinvoice.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single row that remembers the hash of the newest audit event.
 * AuditService locks this row while appending, so two writers can never chain onto the same previous hash.
 */
@Entity
@Table(name = "audit_head")
@Getter
@Setter
@NoArgsConstructor
public class AuditHead {

    public static final long ID = 1L;

    @Id
    private Long id = ID;
    private String lastHash = AuditHasher.GENESIS;
}
