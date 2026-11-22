package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VirtualCardRepository extends JpaRepository<VirtualCard, Long> {

    Optional<VirtualCard> findByPayment_Id(String paymentId);
}
