package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.FlagSeverity;
import com.brim.easyinvoice.model.ApprovalStatus;
import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import com.brim.easyinvoice.security.ForbiddenException;
import com.brim.easyinvoice.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a person does to an invoice. Same shape every time:
 *   1. authorize()   2. record the decision   3. audit it   4. queue the node that reacts to it
 *
 * Each method is one transaction. A 403 does NOT roll back, so the "authz.denied" audit event
 * that authorize() wrote is kept (refused attempts must stay on record).
 */
@Service
@Transactional(noRollbackFor = ForbiddenException.class)
public class HumanActionService {

    private final InvoiceRepository invoices;
    private final ApprovalStepRepository steps;
    private final PaymentRepository payments;
    private final FlagRepository flags;
    private final AuthorizationService authorization;
    private final InvoiceStateMachine stateMachine;
    private final AuditService audit;
    private final PipelineRunner runner;

    public HumanActionService(InvoiceRepository invoices, ApprovalStepRepository steps, PaymentRepository payments,
                              FlagRepository flags, AuthorizationService authorization, InvoiceStateMachine stateMachine,
                              AuditService audit, PipelineRunner runner) {
        this.invoices = invoices;
        this.steps = steps;
        this.payments = payments;
        this.flags = flags;
        this.authorization = authorization;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.runner = runner;
    }

    // ---------- Approval execution ----------

    public void approve(User user, String invoiceId, String comment) {
        Invoice invoice = invoices.get(invoiceId);
        authorization.authorize(user, Action.APPROVE_INVOICE, invoice);
        ApprovalStep step = currentStep(invoice);

        recordDecision(step, ApprovalStatus.APPROVED, user, comment);
        audit.record(Actor.of(user, step.getActingForId()), invoice.getId(), null, "approval.approved",
                NodeName.APPROVAL_EXECUTION.name(), null, null, details("stepNo", step.getStepNo(), "comment", comment));
        runner.enqueue(invoice, NodeName.APPROVAL_EXECUTION);
    }

    public void reject(User user, String invoiceId, String reason) {
        requireReason(reason);
        Invoice invoice = invoices.get(invoiceId);
        authorization.authorize(user, Action.APPROVE_INVOICE, invoice);
        ApprovalStep step = currentStep(invoice);

        recordDecision(step, ApprovalStatus.REJECTED, user, reason);
        audit.record(Actor.of(user, step.getActingForId()), invoice.getId(), null, "approval.rejected",
                NodeName.APPROVAL_EXECUTION.name(), null, null, details("stepNo", step.getStepNo(), "reason", reason));
        stateMachine.reject(invoice, Actor.of(user, step.getActingForId()), reason);
    }

    /** "Return to AP": back to the Validation failed queue; existing approvals are voided. */
    public void returnToAp(User user, String invoiceId, String reason) {
        requireReason(reason);
        Invoice invoice = invoices.get(invoiceId);
        authorization.authorize(user, Action.APPROVE_INVOICE, invoice);
        ApprovalStep step = currentStep(invoice);

        recordDecision(step, ApprovalStatus.RETURNED, user, reason);
        voidOpenSteps(invoice.getId());
        Actor actor = Actor.of(user, step.getActingForId());
        audit.record(actor, invoice.getId(), null, "approval.returned", NodeName.APPROVAL_EXECUTION.name(),
                null, null, details("stepNo", step.getStepNo(), "reason", reason));
        stateMachine.hold(invoice, HoldQueue.VALIDATION_FAILED, "RETURNED_BY_APPROVER", reason, actor,
                NodeName.APPROVAL_EXECUTION.name());
    }

    // ---------- Exception queues ----------

    /** Admin fixed the problem (or overrides it with a written reason): resume at the queue's node. */
    public void resumeFromHold(User user, String invoiceId, String reason) {
        requireReason(reason);
        Invoice invoice = invoices.get(invoiceId);
        authorization.authorize(user, Action.RESOLVE_HOLD, invoice);

        // The blocking flags stay visible, marked as overridden with the Admin's written reason.
        // On a fraud hold also override the score's individual signals, so re-entering FRAUD_SCORING
        // does not block again on the same, already reviewed, signals.
        boolean fraudHold = HoldQueue.FRAUD_HOLD.name().equals(invoice.getHoldQueue());
        List<String> overriddenSignals = new ArrayList<>();
        for (Flag flag : flags.findByInvoice_IdOrderById(invoice.getId())) {
            if (flag.isOverridden()) continue;
            boolean blockingFlag = flag.getSeverity() == FlagSeverity.BLOCK;
            boolean fraudSignal = fraudHold && NodeName.FRAUD_SCORING.name().equals(flag.getNode());
            if (!blockingFlag && !fraudSignal) continue;
            flag.setOverriddenBy(user);
            flag.setOverrideReason(reason);
            if (fraudSignal) overriddenSignals.add(flag.getCode());
        }
        Map<String, Object> details = new HashMap<>();
        if (fraudHold) {
            details.put("riskScore", invoice.getRiskScore());
            details.put("overriddenSignals", overriddenSignals);
        }
        NodeName resumeAt = stateMachine.releaseHold(invoice, Actor.of(user), reason, details);
        runner.enqueue(invoice, resumeAt);
    }

    public void rejectFromHold(User user, String invoiceId, String reason) {
        requireReason(reason);
        Invoice invoice = invoices.get(invoiceId);
        authorization.authorize(user, Action.RESOLVE_HOLD, invoice);
        stateMachine.reject(invoice, Actor.of(user), reason);
    }

    // ---------- Payment release ----------

    /** Kept for callers that do not supply an optional note. */
    public void release(User user, String paymentId) {
        release(user, paymentId, null);
    }

    public void release(User user, String paymentId, String reason) {
        Payment payment = payments.get(paymentId);
        Invoice invoice = invoices.get(payment.getInvoiceId());
        authorization.authorize(user, Action.RELEASE_PAYMENT, invoice);
        if (payment.getStatus() != PaymentStatus.SCHEDULED) {
            throw new BadRequestException("Payment " + paymentId + " is " + payment.getStatus() + ", not SCHEDULED");
        }

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedBy(user);
        payment.setReleasedAt(OffsetDateTime.now());
        Map<String, Object> releaseDetails = details("amount", payment.getAmount(), "currency", payment.getCurrency(),
                "method", payment.getMethod());
        if (reason != null && !reason.isBlank()) releaseDetails.put("reason", reason.trim());
        audit.record(Actor.of(user), invoice.getId(), payment.getId(), "payment.released",
                NodeName.PAYMENT_RELEASE.name(), null, null, releaseDetails);
        stateMachine.moveTo(invoice, InvoiceStatus.RELEASED, Actor.of(user), NodeName.PAYMENT_RELEASE.name(), null);
        runner.enqueue(invoice, NodeName.PAYMENT_RELEASE);
    }

    // ---------- helpers ----------

    private ApprovalStep currentStep(Invoice invoice) {
        return AuthorizationService.currentStep(steps.forInvoice(invoice.getId()))
                .orElseThrow(() -> new BadRequestException("No open approval step"));
    }

    /** The step is a managed entity, so these changes are saved when the transaction commits. */
    private static void recordDecision(ApprovalStep step, ApprovalStatus status, User user, String comment) {
        step.setStatus(status);
        step.setActedBy(user);
        step.setActedAt(OffsetDateTime.now());
        step.setComment(comment);
    }

    /** Existing approvals no longer count once the invoice goes back to AP. */
    private void voidOpenSteps(String invoiceId) {
        for (ApprovalStep step : steps.forInvoice(invoiceId)) {
            if (step.isPending() || step.isApproved()) step.setStatus(ApprovalStatus.VOID);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A written reason is required");
        }
    }

    /** Map.of() refuses nulls (e.g. an empty comment), so build the payload by hand. */
    private static Map<String, Object> details(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Map<String, Object> details(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> map = details(k1, v1, k2, v2);
        map.put(k3, v3);
        return map;
    }
}
