package com.brim.easyinvoice.audit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AuditHeadRepository extends JpaRepository<AuditHead, Long> {

    /** Reads the head row and locks it until the surrounding transaction ends (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditHead> findForUpdateById(Long id);
}
