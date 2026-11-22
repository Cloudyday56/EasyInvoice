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
        List<ApprovalStep> steps = invoice == null ? List.of() : approvalSteps.forInvoice(invoice.getId());
        Decision decision = decide(user, action, invoice, steps);
        if (!decision.allowed()) {
            audit.record(Actor.of(user), invoice == null ? null : invoice.getId(), null, "authz.denied", null,
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
            return Decision.deny("User " + user.getId() + " is no longer active");
        }
        return switch (action) {
            case VIEW_INVOICE -> canViewInvoice(user, invoice, steps);
            case UPLOAD_INVOICE -> user.getRole() == Role.EMPLOYEE || user.getRole() == Role.ADMIN
                    ? Decision.allow()
                    : Decision.deny("Only Employees and Admins upload invoices");
            case APPROVE_INVOICE -> canApprove(user, invoice, steps);
            case RESOLVE_HOLD -> canResolveHold(user, invoice);
            case RELEASE_PAYMENT -> canRelease(user, invoice, steps);
            case VIEW_ADMIN_SCREENS, RUN_ADMIN_TOOLS -> user.getRole() == Role.ADMIN
                    ? Decision.allow()
                    : Decision.deny("Admins only");
        };
    }

    private static Decision canViewInvoice(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        return switch (user.getRole()) {
            case ADMIN -> Decision.allow();
            case APPROVER -> Objects.equals(user.getDepartmentId(), invoice.getDepartmentId())
                    || isAssignedToAnyStep(user, steps)
                    ? Decision.allow()
                    : Decision.deny("Approvers only see their own department's invoices");
            case EMPLOYEE -> user.getId().equals(invoice.getRequesterId())
                    ? Decision.allow()
                    : Decision.deny("Employees only see invoices they submitted");
        };
    }

    private static Decision canApprove(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.getRole() == Role.EMPLOYEE) {
            return Decision.deny("Employees cannot approve");
        }
        if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) {
            return Decision.deny("Invoice is not waiting for approval (status " + invoice.getStatus() + ")");
        }
        // Separation of duties #1: nobody approves their own invoice.
        if (user.getId().equals(invoice.getRequesterId())) {
            return Decision.deny("No self-approval: you requested this invoice");
        }
        Optional<ApprovalStep> current = currentStep(steps);
        if (current.isEmpty()) {
            return Decision.deny("No approval step is open");
        }
        // Delegates are stored as the assignee (with acting_for = the person on leave).
        if (!user.getId().equals(current.get().getAssignedUserId())) {
            return Decision.deny("You are not the assigned approver for step " + current.get().getStepNo());
        }
        // Department scope: an Approver acts for their own department, unless standing in as a delegate.
        if (user.getRole() == Role.APPROVER && current.get().getActingFor() == null
                && !Objects.equals(user.getDepartmentId(), invoice.getDepartmentId())) {
            return Decision.deny("Invoice belongs to another department");
        }
        // Separation of duties: one person never signs two steps of the same invoice.
        boolean alreadyApproved = steps.stream().anyMatch(s -> s.isApproved() && user.getId().equals(s.getActedById()));
        if (alreadyApproved) {
            return Decision.deny("You already approved a step of this invoice");
        }
        // Limit read from the user row at the moment of the action (never cached). An Approver is always
        // capped; an Admin in the middle of a chain (finance manager before the controller) signs a tier
        // and is only capped on the last step. Credit notes are negative, so compare the size of the amount.
        if (invoice.getTotalCad() == null || user.getApprovalLimitCad() == null) {
            return Decision.deny("Invoice amount or your approval limit is unknown");
        }
        boolean lastStep = steps.stream().noneMatch(s -> s.getStepNo() > current.get().getStepNo()
                && (s.isPending() || s.isApproved()));
        if ((user.getRole() == Role.APPROVER || lastStep)
                && invoice.getTotalCad().abs().compareTo(user.getApprovalLimitCad()) > 0) {
            return Decision.deny("Amount " + invoice.getTotalCad() + " CAD is above your approval limit of "
                    + user.getApprovalLimitCad());
        }
        return Decision.allow();
    }

    private static Decision canResolveHold(User user, Invoice invoice) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.getRole() != Role.ADMIN) {
            return Decision.deny("Only Admins work exception queues");
        }
        if (invoice.getStatus() != InvoiceStatus.ON_HOLD && invoice.getStatus() != InvoiceStatus.ON_HOLD_VENDOR) {
            return Decision.deny("Invoice is not on hold");
        }
        // Separation of duties: nobody clears or rejects an exception on an invoice they uploaded themselves.
        if (user.getId().equals(invoice.getRequesterId())) {
            return Decision.deny("No self-review: you requested this invoice");
        }
        return Decision.allow();
    }

    private static Decision canRelease(User user, Invoice invoice, List<ApprovalStep> steps) {
        if (invoice == null) return Decision.deny("No invoice");
        if (user.getRole() != Role.ADMIN) {
            return Decision.deny("Only Admins release payments");
        }
        if (invoice.getStatus() != InvoiceStatus.SCHEDULED) {
            return Decision.deny("Payment is not scheduled (invoice status " + invoice.getStatus() + ")");
        }
        // Separation of duties: an Admin who uploaded the invoice cannot release its payment either.
        if (user.getId().equals(invoice.getRequesterId())) {
            return Decision.deny("No self-release: you requested this invoice");
        }
        // Separation of duties: whoever approved a step (or was stood in for by a delegate who did) cannot release.
        boolean approvedAStep = steps.stream().anyMatch(s -> s.isApproved()
                && (user.getId().equals(s.getActedById()) || user.getId().equals(s.getActingForId())));
        if (approvedAStep) {
            return Decision.deny("Approver cannot release: you approved a step of this invoice");
        }
        return Decision.allow();
    }

    /** The step waiting for a decision: lowest step number that is still PENDING. */
    public static Optional<ApprovalStep> currentStep(List<ApprovalStep> steps) {
        return steps.stream()
                .filter(ApprovalStep::isPending)
                .min(Comparator.comparingInt(ApprovalStep::getStepNo));
    }

    private static boolean isAssignedToAnyStep(User user, List<ApprovalStep> steps) {
        return steps.stream().anyMatch(s -> user.getId().equals(s.getAssignedUserId()));
    }
}
