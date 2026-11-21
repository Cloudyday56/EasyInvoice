package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, String> {
}
