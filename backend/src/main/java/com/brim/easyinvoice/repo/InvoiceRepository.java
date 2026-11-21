package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    default Invoice get(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
    }

    /** Newest first. Fine for a few hundred rows; would need paging at real volume. */
    List<Invoice> findAllByOrderByCreatedAtDescIdDesc();

    List<Invoice> findByStatus(InvoiceStatus status);

    /** Sorted by due date, as the "Needs attention" inbox shows them. */
    @Query("select i from Invoice i where i.status in :statuses order by i.dueDate asc nulls last, i.createdAt")
    List<Invoice> findByStatusesByDueDate(@Param("statuses") Collection<InvoiceStatus> statuses);

    default List<Invoice> findOnHold() {
        return findByStatusesByDueDate(List.of(InvoiceStatus.ON_HOLD, InvoiceStatus.ON_HOLD_VENDOR));
    }

    Optional<Invoice> findBySourceDocumentId(Long sourceDocumentId);

    /** Repeat guard: does another invoice from this vendor already have this canonical number? */
    boolean existsByVendorIdAndCanonicalInvoiceNumberAndIdNot(String vendorId, String canonicalNumber, String invoiceId);

    /** Next number for ids like EI-00001 (new invoices; the seeded history keeps its AP-xxxxx ids). */
    @Query(value = "select nextval('new_invoice_seq')", nativeQuery = true)
    long nextInvoiceNumber();

    /** Written before a node runs, so the UI can show which node is working. */
    @Modifying
    @Transactional
    @Query("update Invoice i set i.currentNode = :node where i.id = :id")
    void updateCurrentNode(@Param("id") String id, @Param("node") String node);
}
