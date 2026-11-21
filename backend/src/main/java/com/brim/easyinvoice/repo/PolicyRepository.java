package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, String> {
}
