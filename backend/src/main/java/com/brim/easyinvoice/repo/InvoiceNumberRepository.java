package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.InvoiceNumber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceNumberRepository extends JpaRepository<InvoiceNumber, Long> {
}
