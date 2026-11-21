package com.brim.easyinvoice.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    /** Loads the invoice together with the rows a pipeline node reads, so they stay usable outside a transaction. */
    @Override
    @EntityGraph(attributePaths = {"vendor", "department", "requester", "sourceDocument", "purchaseOrder", "contract"})
    Optional<Invoice> findById(String id);

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

    Optional<Invoice> findBySourceDocument_Id(Long sourceDocumentId);

    /** Repeat guard: does another invoice from this vendor already have this canonical number? */
    boolean existsByVendor_IdAndCanonicalInvoiceNumberAndIdNot(String vendorId, String canonicalNumber, String invoiceId);

    /** Prior invoices for the same vendor, used by the amount-outlier signal at N6. */
    List<Invoice> findByVendor_IdAndIdNot(String vendorId, String invoiceId);

    /** Invoices from the same vendor in a date window, used by the split-invoice signal at N6. */
    @Query("select i from Invoice i where i.vendor.id = :vendorId and i.id <> :invoiceId " +
            "and i.invoiceDate between :from and :to")
    List<Invoice> findByVendorAndDateWindow(@Param("vendorId") String vendorId,
                                            @Param("invoiceId") String invoiceId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Written before a node runs, so the UI can show which node is working. */
    @Modifying
    @Transactional
    @Query("update Invoice i set i.currentNode = :node where i.id = :id")
    void updateCurrentNode(@Param("id") String id, @Param("node") String node);
}
