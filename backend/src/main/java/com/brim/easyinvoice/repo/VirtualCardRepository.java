package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualCardRepository extends JpaRepository<VirtualCard, Long> {
}
