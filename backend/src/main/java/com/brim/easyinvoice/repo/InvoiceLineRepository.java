package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoice_IdOrderByLineNo(String invoiceId);
}
