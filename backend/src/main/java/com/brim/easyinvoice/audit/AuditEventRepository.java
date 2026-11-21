package com.brim.easyinvoice.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderById();

    List<AuditEvent> findByInvoiceIdOrderById(String invoiceId);

    /** Newest first; pass PageRequest.ofSize(n) to limit. */
    List<AuditEvent> findAllByOrderByIdDesc(Pageable limit);

    Optional<AuditEvent> findFirstByOrderByIdDesc();
}
