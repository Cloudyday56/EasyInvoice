package com.brim.easyinvoice.security;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The ONE place that decides who may do what. Every endpoint calls authorize() before acting;
 * the UI only hides buttons this class would refuse anyway.
 *
 * The rules live in the static decide() method, which has no database access, so every rule
 * can be unit-tested with plain objects (see AuthorizationServiceTest).
 */
@Service
public class AuthorizationService {

    private final ApprovalStepRepository approvalSteps;
    private final AuditService audit;

    public AuthorizationService(ApprovalStepRepository approvalSteps, AuditService audit) {
        this.approvalSteps = approvalSteps;
        this.audit = audit;
    }

    /** Throws 403 (and writes an audit event) if the user may not do this. */
    public void authorize(User user, Action action, Invoice invoice) {
        List<ApprovalStep> steps = invoice == null ? List.of() : approvalSteps.forInvoice(invoice.id());
        Decision decision = decide(user, action, invoice, steps);
        if (!decision.allowed()) {
            audit.record(Actor.of(user), invoice == null ? null : invoice.id(), null, "authz.denied", null,
                    null, null, Map.of("action", action.name(), "reason", decision.reason()));
            throw new ForbiddenException(decision.reason());
        }
    }

    /** Which actions the UI should offer for this invoice. Same rules, no audit noise. */
    public List<Action> allowedActions(User user, Invoice invoice, List<ApprovalStep> steps) {
        List<Action> allowed = new ArrayList<>();
        for (Action action : List.of(Action.APPROVE_INVOICE, Action.RESOLVE_HOLD, Action.RELEASE_PAYMENT)) {
            if (decide(user, action, invoice, steps).allowed()) allowed.add(action);
        }
        return allowed;
    }

    public boolean canView(User user, Invoice invoice, List<ApprovalStep> steps) {
        return decide(user, Action.VIEW_INVOICE, invoice, steps).allowed();
    }

    // ------------------------------------------------------------------
    // The rules. Pure function: same inputs, same answer, no side effects.
    // ------------------------------------------------------------------

    public static Decision decide(User user, Action action, Invoice invoice, List<ApprovalStep> steps) {
        if (!user.isActive()) {
            return Decision.deny("User " + user.id() + " is no longer active");
        }
        if (user.readOnly() && action.changesData()) {
            return Decision.deny("Read-only users (auditor) cannot change anything");
        }
        return switch (action) {
            case VIEW_INVOICE -> canViewInvoice(user, invoice, steps);
            case UPLOAD_INVOICE -> user.role() == Role.EMPLOYEE || user.role() == Role.ADMIN
                    ? Decision.allow()
                    : Decision.deny("Only Employees and Admins upload invoices");
            case APPROVE_INVOICE -> canApprove(user, invoice, steps);
            case RESOLVE_HOLD -> canResolveHold(user, invoice);
            case RELEASE_PAYMENT -> canRelease(user, invoice, steps);
            case VIEW_ADMIN_SCREENS, RUN_ADMIN_TOOLS -> user.role() == Role.ADMIN
                    ? Decision.allow()
                    : Decision.deny("Admins only");
        };
    }

    private static Decision canViewInvoice(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        return switch (user.role()) {
            case ADMIN -> Decision.allow();
            case APPROVER -> Objects.equals(user.departmentId(), invoice.departmentId())
                    || isAssignedToAnyStep(user, steps)
                    ? Decision.allow()
                    : Decision.deny("Approvers only see their own department's invoices");
            case EMPLOYEE -> user.id().equals(invoice.requesterUserId())
                    ? Decision.allow()
                    : Decision.deny("Employees only see invoices they submitted");
        };
    }

    private static Decision canApprove(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.role() == Role.EMPLOYEE) {
            return Decision.deny("Employees cannot approve");
        }
        if (invoice.status() != InvoiceStatus.PENDING_APPROVAL) {
            return Decision.deny("Invoice is not waiting for approval (status " + invoice.status() + ")");
        }
        // Separation of duties #1: nobody approves their own invoice.
        if (user.id().equals(invoice.requesterUserId())) {
            return Decision.deny("No self-approval: you requested this invoice");
        }
        Optional<ApprovalStep> current = currentStep(steps);
        if (current.isEmpty()) {
            return Decision.deny("No approval step is open");
        }
        // Delegates are stored as the assignee (with acting_for = the person on leave).
        if (!user.id().equals(current.get().assignedUserId())) {
            return Decision.deny("You are not the assigned approver for step " + current.get().stepNo());
        }
        // Department scope: an Approver acts for their own department, unless standing in as a delegate.
        if (user.role() == Role.APPROVER && current.get().actingFor() == null
                && !Objects.equals(user.departmentId(), invoice.departmentId())) {
            return Decision.deny("Invoice belongs to another department");
        }
        // Limit read from the user row at the moment of the action (never cached).
        if (invoice.totalCad() == null) {
            return Decision.deny("Invoice amount is unknown");
        }
        if (invoice.totalCad().compareTo(user.approvalLimitCad()) > 0) {
            return Decision.deny("Amount " + invoice.totalCad() + " CAD is above your approval limit of "
                    + user.approvalLimitCad());
        }
        return Decision.allow();
    }

    private static Decision canResolveHold(User user, Invoice invoice) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.role() != Role.ADMIN) {
            return Decision.deny("Only Admins work exception queues");
        }
        if (invoice.status() != InvoiceStatus.ON_HOLD && invoice.status() != InvoiceStatus.ON_HOLD_VENDOR) {
            return Decision.deny("Invoice is not on hold");
        }
        return Decision.allow();
    }

    private static Decision canRelease(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.role() != Role.ADMIN) {
            return Decision.deny("Only Admins release payments");
        }
        if (invoice.status() != InvoiceStatus.SCHEDULED) {
            return Decision.deny("Payment is not scheduled (invoice status " + invoice.status() + ")");
        }
        // Separation of duties #2: whoever approved any step cannot release the payment.
        boolean approvedAStep = steps.stream()
                .anyMatch(s -> s.isApproved() && user.id().equals(s.actedBy()));
        if (approvedAStep) {
            return Decision.deny("Approver cannot release: you approved a step of this invoice");
        }
        BigDecimal amount = invoice.totalCad();
        if (amount == null) {
            return Decision.deny("Invoice amount is unknown");
        }
        if (amount.compareTo(user.releaseLimitCad()) > 0) {
            return Decision.deny("Amount " + amount + " CAD is above your release limit of " + user.releaseLimitCad());
        }
        return Decision.allow();
    }

    /** The step waiting for a decision: lowest step number that is still PENDING. */
    public static Optional<ApprovalStep> currentStep(List<ApprovalStep> steps) {
        return steps.stream()
                .filter(ApprovalStep::isPending)
                .min(Comparator.comparingInt(ApprovalStep::stepNo));
    }

    private static boolean isAssignedToAnyStep(User user, List<ApprovalStep> steps) {
        return steps.stream().anyMatch(s -> user.id().equals(s.assignedUserId()));
    }
}
