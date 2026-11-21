package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    default Payment get(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Payment " + id + " not found"));
    }

    @Query("select p from Payment p order by p.scheduledDate desc nulls last, p.id desc")
    List<Payment> findAllNewestFirst();

    List<Payment> findByInvoiceIdOrderById(String invoiceId);

    Optional<Payment> findFirstByInvoiceIdAndStatusNotOrderByIdDesc(String invoiceId, String status);

    /** The invoice's live payment: the newest one that has not failed. */
    default Optional<Payment> activeForInvoice(String invoiceId) {
        return findFirstByInvoiceIdAndStatusNotOrderByIdDesc(invoiceId, "FAILED");
    }

    /** Next number for ids like EP-00001. */
    @Query(value = "select nextval('new_payment_seq')", nativeQuery = true)
    long nextPaymentNumber();
}
