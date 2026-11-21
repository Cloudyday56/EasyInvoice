package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.BankChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankChangeRequestRepository extends JpaRepository<BankChangeRequest, String> {
}
