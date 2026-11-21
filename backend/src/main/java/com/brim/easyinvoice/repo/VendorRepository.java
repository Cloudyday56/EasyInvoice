package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, String> {

    Optional<Vendor> findFirstByLegalNameIgnoreCase(String legalName);

    List<Vendor> findByStatus(String status);

    default List<Vendor> findAllActive() {
        return findByStatus("active");
    }
}
