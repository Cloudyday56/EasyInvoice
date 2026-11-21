package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    List<Flag> findByInvoiceIdOrderById(String invoiceId);

    /** Blocking flags nobody has overridden yet. */
    List<Flag> findByInvoiceIdAndSeverityAndOverriddenByIsNull(String invoiceId, String severity);

    /** Re-running a node replaces its earlier flags (nodes are idempotent). Overridden flags stay. */
    void deleteByInvoiceIdAndNodeAndOverriddenByIsNull(String invoiceId, String node);
}
