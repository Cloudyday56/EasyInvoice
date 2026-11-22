package com.brim.easyinvoice.pipeline.nodes;

import org.springframework.transaction.annotation.Transactional;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Department;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.model.VendorStatus;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.DepartmentRepository;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Job: build the chain of approvers this invoice needs (policies P01-P08).
 *
 *   1. Department manager, if the amount is within their limit (an Approver never signs above it).
 *   2. Finance manager: total over $10,000, vendor pending review or onboarded in the last 90 days, or a Legal vendor.
 *   3. Controller: total over $50,000.   4. CFO: total over $250,000.
 *
 * Each person goes through the same rules (self-approval escalates to their manager, on leave goes to the
 * delegate, terminated escalates). Nobody appears twice in one chain, and the last approver must be able to
 * cover the full amount, otherwise the invoice goes to the routing error queue.
 *
 * TODO: add an Admin step for FLAGs from Validation rules / Fraud scoring that require it.
 */
@Component
public class ApprovalRoutingNode implements Node {

    /** Stop following manager chains after this many hops (guards against loops in the data). */
    private static final int MAX_ESCALATIONS = 5;
    private static final BigDecimal FINANCE_MANAGER_OVER = new BigDecimal("10000");
    private static final BigDecimal CONTROLLER_OVER = new BigDecimal("50000");
    private static final BigDecimal CFO_OVER = new BigDecimal("250000");
    private static final int NEW_VENDOR_DAYS = 90;

    private final DepartmentRepository departments;
    private final ApprovalStepRepository steps;
    private final UserRepository users;
    private final InvoiceRepository invoices;

    public ApprovalRoutingNode(DepartmentRepository departments, ApprovalStepRepository steps, UserRepository users,
                               InvoiceRepository invoices) {
        this.invoices = invoices;
        this.departments = departments;
        this.steps = steps;
        this.users = users;
    }

    @Override
    public NodeName name() {
        return NodeName.APPROVAL_ROUTING;
    }

    /** Who will approve, on whose behalf, and why. */
    record Assignment(User approver, User actingFor, String reason) {
    }

    /** Transactional so the user, manager and delegate links can be followed (the nodes run outside a transaction). */
    @Override
    @Transactional
    public StageResult run(Invoice invoice, NodeContext ctx) {
        List<ApprovalStep> live = steps.forInvoice(invoice.getId()).stream()
                .filter(s -> s.isPending() || s.isApproved()).toList();
        if (!live.isEmpty()) {
            return StageResult.pass("Approval chain already exists (" + live.size() + " steps)"); // idempotent
        }
        // An emailed invoice has no requester or department. Route it to the department this vendor was last billed to.
        Department inferred = null;
        String departmentId = invoice.getDepartmentId();
        if (departmentId == null && invoice.getVendor() != null) {
            inferred = invoices.findFirstByVendor_IdAndDepartmentIsNotNullAndIdNotOrderByCreatedAtDescIdDesc(
                    invoice.getVendor().getId(), invoice.getId()).map(Invoice::getDepartment).orElse(null);
            if (inferred != null) departmentId = inferred.getId();
        }
        if (departmentId == null) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_DEPARTMENT",
                    "No department: the requester is unknown and this vendor has no earlier invoice to copy it from");
        }
        Optional<User> manager = departments.findById(departmentId).map(Department::getManager);
        if (manager.isEmpty()) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_APPROVER",
                    "Department " + departmentId + " has no manager");
        }
        if (invoice.getTotalCad() == null) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_AMOUNT", "Invoice amount in CAD is unknown");
        }
        BigDecimal total = invoice.getTotalCad().abs();
        String requesterId = invoice.getRequesterId();

        List<Assignment> chain = new ArrayList<>();
        resolve(manager.get(), requesterId, "Department manager")
                .filter(a -> within(a.approver(), total))
                .ifPresent(chain::add);

        Vendor vendor = invoice.getVendor();
        if (total.compareTo(FINANCE_MANAGER_OVER) > 0) {
            addAdmin(chain, "finance_manager", "Total over $10,000", requesterId, false);
        } else if (vendor != null && vendor.getStatus() == VendorStatus.PENDING_REVIEW) {
            addAdmin(chain, "finance_manager", "Vendor is pending review", requesterId, false);
        } else if (vendor != null && vendor.getOnboardedAt() != null
                && vendor.getOnboardedAt().isAfter(LocalDate.now().minusDays(NEW_VENDOR_DAYS))) {
            addAdmin(chain, "finance_manager", "New vendor (onboarded in the last " + NEW_VENDOR_DAYS + " days)",
                    requesterId, false);
        } else if (vendor != null && "Legal".equalsIgnoreCase(vendor.getCategory())) {
            addAdmin(chain, "finance_manager", "Legal vendor", requesterId, false);
        }
        if (total.compareTo(CONTROLLER_OVER) > 0) {
            addAdmin(chain, "controller_admin", "Total over $50,000", requesterId, false);   // lowest-limit controller
        }
        if (total.compareTo(CFO_OVER) > 0) {
            addAdmin(chain, "controller_admin", "Total over $250,000", requesterId, true);   // highest-limit: the CFO
        }

        if (chain.isEmpty() || !within(chain.get(chain.size() - 1).approver(), total)) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_ELIGIBLE_APPROVER",
                    "No approver can cover " + total + " CAD for department " + departmentId);
        }

        for (int i = 0; i < chain.size(); i++) {
            Assignment a = chain.get(i);
            ApprovalStep step = new ApprovalStep();
            step.setInvoice(invoice);
            step.setStepNo(i + 1);
            step.setRequiredRole(a.approver().getRole());
            step.setReason(a.reason());
            step.setAssignedUser(a.approver());
            step.setActingFor(a.actingFor());
            steps.save(step);
        }
        String chainText = "Approval chain: " + chain.stream()
                .map(a -> a.approver().getFullName()).collect(Collectors.joining(" > "));
        if (inferred == null) return StageResult.pass(chainText);
        return StageResult.pass(chainText + " (department " + departmentId + " copied from this vendor's last invoice)",
                Map.of("department", inferred));
    }

    /** Adds the Admin with this dataset role, unless that person (after the person rules) is already in the chain. */
    private void addAdmin(List<Assignment> chain, String sourceRole, String why, String requesterId, boolean highestLimit) {
        Comparator<User> byLimit = Comparator.comparing(User::getApprovalLimitCad, Comparator.nullsFirst(Comparator.naturalOrder()));
        users.findBySourceRoleAndStatus(sourceRole, UserStatus.ACTIVE).stream()
                .max(highestLimit ? byLimit : byLimit.reversed())
                .flatMap(admin -> resolve(admin, requesterId, why))
                .filter(a -> chain.stream().noneMatch(c -> c.approver().getId().equals(a.approver().getId())))
                .ifPresent(chain::add);
    }

    private static boolean within(User approver, BigDecimal total) {
        return approver.getApprovalLimitCad() != null && total.compareTo(approver.getApprovalLimitCad()) <= 0;
    }

/** Applies the person rules until we land on someone who can actually approve. */
    Optional<Assignment> resolve(User candidate, String requesterId, String initialReason) {
        String reason = initialReason;
        for (int hop = 0; hop < MAX_ESCALATIONS && candidate != null; hop++) {
            if (!candidate.isActive()) {
                reason = candidate.getFullName() + " has left; escalated to their manager";
                candidate = managerOf(candidate);
            } else if (candidate.getRole() == Role.EMPLOYEE) {
                reason = candidate.getFullName() + " cannot approve; escalated to their manager";
                candidate = managerOf(candidate);
            } else if (candidate.getId().equals(requesterId)) {
                reason = "Requester is the approver; escalated to their manager (no self-approval)";
                candidate = managerOf(candidate);
            } else if (candidate.isOnLeave()) {
                User delegate = candidate.getDelegate();
                boolean usable = delegate != null && delegate.isActive() && !delegate.isOnLeave()
                        && delegate.getRole() != Role.EMPLOYEE && !delegate.getId().equals(requesterId);
                if (usable) {
                    return Optional.of(new Assignment(delegate, candidate,
                            candidate.getFullName() + " is on leave; delegate assigned"));
                }
                reason = candidate.getFullName() + " is on leave with no usable delegate; escalated to their manager";
                candidate = managerOf(candidate);
            } else {
                return Optional.of(new Assignment(candidate, null, reason));
            }
        }
        return Optional.empty();
    }

    private User managerOf(User user) {
        return user.getManager();
    }
}
