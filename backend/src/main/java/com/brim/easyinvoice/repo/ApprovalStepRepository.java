package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.ApprovalStep;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    @Query("select s from ApprovalStep s where s.invoice.id = :invoiceId order by s.stepNo, s.id")
    List<ApprovalStep> forInvoice(@Param("invoiceId") String invoiceId);

    @Query("select s from ApprovalStep s where s.assignedUser.id = :userId and s.status = com.brim.easyinvoice.model.ApprovalStatus.PENDING order by s.createdAt")
    List<ApprovalStep> pendingAssignedTo(@Param("userId") String userId);

    /** All steps grouped by invoice: one query for list screens instead of one per invoice. */
    default Map<String, List<ApprovalStep>> allGroupedByInvoice() {
        return findAll(Sort.by("stepNo", "id")).stream().collect(Collectors.groupingBy(ApprovalStep::getInvoiceId));
    }
}
