package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.PaymentNumber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentNumberRepository extends JpaRepository<PaymentNumber, Long> {
}
