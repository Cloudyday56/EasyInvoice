package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, String> {

    List<CardTransaction> findByPayment_Id(String paymentId);
}
