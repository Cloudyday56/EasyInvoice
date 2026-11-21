package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, String> {
}
