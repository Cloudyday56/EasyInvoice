package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Job: build the list of approvers this invoice needs.
 * Partly real: one step for the department manager, with the person-resolution rules
 * (self-approval escalates, on leave goes to the delegate, terminated skips to the manager).
 *
 * TODO: evaluate the JSON rows in `policies` in order (amount tiers, new vendor, Legal category)
 *       and add ADMIN steps; escalate to Admin above the approver's limit; add an Admin step for
 *       FLAGs from Validation rules / Fraud scoring that require it.
 */
@Component
public class ApprovalRoutingNode implements Node {

    /** Stop following manager chains after this many hops (guards against loops in the data). */
    private static final int MAX_ESCALATIONS = 5;

    private final UserRepository users;
    private final ApprovalStepRepository steps;

    public ApprovalRoutingNode(UserRepository users, ApprovalStepRepository steps) {
        this.users = users;
        this.steps = steps;
    }

    @Override
    public NodeName name() {
        return NodeName.APPROVAL_ROUTING;
    }

    /** Who will approve, on whose behalf, and why. */
    record Assignment(User approver, String actingFor, String reason) {
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        List<ApprovalStep> live = steps.forInvoice(invoice.id()).stream()
                .filter(s -> s.isPending() || s.isApproved()).toList();
        if (!live.isEmpty()) {
            return StageResult.pass("Approval chain already exists (" + live.size() + " steps)"); // idempotent
        }
        if (invoice.departmentId() == null) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_DEPARTMENT",
                    "No department: the requester is unknown (e.g. invoice came by email)");
        }
        Optional<String> managerId = users.departmentManagerId(invoice.departmentId());
        if (managerId.isEmpty() || managerId.get() == null) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_APPROVER",
                    "Department " + invoice.departmentId() + " has no manager");
        }

        Optional<Assignment> assignment = resolve(users.get(managerId.get()), invoice.requesterUserId());
        if (assignment.isEmpty()) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_ELIGIBLE_APPROVER",
                    "Could not find an eligible approver for department " + invoice.departmentId());
        }

        Assignment a = assignment.get();
        steps.insert(invoice.id(), 1, a.approver().role().name(), a.reason(), a.approver().id(), a.actingFor());
        return StageResult.pass("Step 1: " + a.approver().fullName() + " (" + a.reason() + ")");
    }

    /** Applies the person rules until we land on someone who can actually approve. */
    Optional<Assignment> resolve(User candidate, String requesterId) {
        String reason = "Department manager";
        for (int hop = 0; hop < MAX_ESCALATIONS && candidate != null; hop++) {
            if (!candidate.isActive()) {
                reason = candidate.fullName() + " has left; escalated to their manager";
                candidate = managerOf(candidate);
            } else if (candidate.id().equals(requesterId)) {
                reason = "Requester is the department manager; escalated to their manager (no self-approval)";
                candidate = managerOf(candidate);
            } else if (candidate.isOnLeave() && candidate.delegateUserId() != null) {
                User delegate = users.get(candidate.delegateUserId());
                if (delegate.id().equals(requesterId) || !delegate.isActive()) {
                    candidate = managerOf(candidate);
                    continue;
                }
                return Optional.of(new Assignment(delegate, candidate.id(),
                        candidate.fullName() + " is on leave; delegate assigned"));
            } else {
                return Optional.of(new Assignment(candidate, null, reason));
            }
        }
        return Optional.empty();
    }

    private User managerOf(User user) {
        return user.managerId() == null ? null : users.findById(user.managerId()).orElse(null);
    }
}
