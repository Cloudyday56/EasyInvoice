package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
}
