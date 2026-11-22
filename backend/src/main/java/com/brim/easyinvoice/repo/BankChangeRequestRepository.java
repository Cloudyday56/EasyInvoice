package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.BankChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BankChangeRequestRepository extends JpaRepository<BankChangeRequest, String> {

    List<BankChangeRequest> findByVendor_IdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(
            String vendorId, LocalDate from);
}
