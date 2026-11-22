package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxCodeRepository extends JpaRepository<TaxCode, String> {
}
