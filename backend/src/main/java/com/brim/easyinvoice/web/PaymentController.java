package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.HumanActionService;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import com.brim.easyinvoice.security.Decision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    /** A payment row plus whether THIS user may release it (and if not, why). */
    public record PaymentRow(Payment payment, String invoiceStatus, boolean canRelease, String releaseBlockedReason) {
    }

    private final CurrentUser currentUser;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final ApprovalStepRepository steps;
    private final AuthorizationService authorization;
    private final HumanActionService actions;

    public PaymentController(CurrentUser currentUser, PaymentRepository payments, InvoiceRepository invoices,
                             ApprovalStepRepository steps, AuthorizationService authorization,
                             HumanActionService actions) {
        this.currentUser = currentUser;
        this.payments = payments;
        this.invoices = invoices;
        this.steps = steps;
        this.authorization = authorization;
        this.actions = actions;
    }

    @GetMapping
    public List<PaymentRow> list(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        User user = currentUser.require(userId);
        authorization.authorize(user, Action.VIEW_ADMIN_SCREENS, null);

        Map<String, Invoice> invoicesById = invoices.findAll().stream()
                .collect(Collectors.toMap(Invoice::id, Function.identity()));
        Map<String, List<ApprovalStep>> stepsByInvoice = steps.allGroupedByInvoice();

        return payments.findAll().stream().map(p -> {
            Invoice invoice = invoicesById.get(p.invoiceId());
            boolean releasable = "SCHEDULED".equals(p.status());
            Decision d = releasable
                    ? AuthorizationService.decide(user, Action.RELEASE_PAYMENT, invoice,
                    stepsByInvoice.getOrDefault(p.invoiceId(), List.of()))
                    : Decision.deny("Payment is " + p.status());
            return new PaymentRow(p, invoice.status().name(), d.allowed(), d.reason());
        }).toList();
    }

    @PostMapping("/{id}/release")
    public void release(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                        @PathVariable String id) {
        actions.release(currentUser.require(userId), id);
    }
}
