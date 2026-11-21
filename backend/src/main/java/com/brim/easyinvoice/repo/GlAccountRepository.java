package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlAccountRepository extends JpaRepository<GlAccount, String> {
}
