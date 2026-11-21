package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.FlagSeverity;
import com.brim.easyinvoice.model.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    List<Flag> findByInvoice_IdOrderById(String invoiceId);

    /** All flags grouped by invoice: one query for the invoice list instead of one per row. */
    default java.util.Map<String, List<Flag>> allGroupedByInvoice() {
        return findAll().stream().collect(java.util.stream.Collectors.groupingBy(Flag::getInvoiceId));
    }

    /** Blocking flags nobody has overridden yet. */
    List<Flag> findByInvoice_IdAndSeverityAndOverriddenByIsNull(String invoiceId, FlagSeverity severity);

}
