package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, String> {
}
