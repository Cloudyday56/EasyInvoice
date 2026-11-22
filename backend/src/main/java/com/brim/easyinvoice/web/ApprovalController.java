package com.brim.easyinvoice.web;

import com.brim.easyinvoice.web.dto.InvoiceDto;
import com.brim.easyinvoice.web.dto.ApprovalStepDto;
import org.springframework.transaction.annotation.Transactional;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import com.brim.easyinvoice.security.Decision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** "My approvals": steps assigned to the current user that are up next on their invoice. */
@RestController
public class ApprovalController {

    public record ApprovalItem(ApprovalStepDto step, InvoiceDto invoice, boolean canApprove, String blockedReason) {
    }

    private final CurrentUser currentUser;
    private final ApprovalStepRepository steps;
    private final InvoiceRepository invoices;

    public ApprovalController(CurrentUser currentUser, ApprovalStepRepository steps, InvoiceRepository invoices) {
        this.currentUser = currentUser;
        this.steps = steps;
        this.invoices = invoices;
    }

    @GetMapping("/api/approvals")
    @Transactional(readOnly = true)
    public List<ApprovalItem> myApprovals(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        User user = currentUser.require(userId);
        List<ApprovalItem> items = new ArrayList<>();
        for (ApprovalStep step : steps.pendingAssignedTo(user.getId())) {
            Invoice invoice = invoices.get(step.getInvoiceId());
            if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) continue;
            List<ApprovalStep> all = steps.forInvoice(invoice.getId());
            boolean isCurrent = AuthorizationService.currentStep(all).map(s -> s.getId().equals(step.getId())).orElse(false);
            if (!isCurrent) continue; // an earlier step still has to be decided first
            Decision decision = AuthorizationService.decide(user, Action.APPROVE_INVOICE, invoice, all);
            items.add(new ApprovalItem(ApprovalStepDto.from(step), InvoiceDto.from(invoice), decision.allowed(), decision.reason()));
        }
        return items;
    }
}
