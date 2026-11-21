package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.notify.NotificationService;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Job: wait for each approval decision, in order. The decisions themselves are made by people
 * through the approve / reject / return endpoints (HumanActionService); each decision re-queues
 * this node, which checks whether every step is now approved.
 *
 * TODO: reminder after 48 h, escalate to the approver's manager after 96 h.
 */
@Component
public class ApprovalExecutionNode implements Node {

    private final ApprovalStepRepository steps;
    private final NotificationService notifications;

    public ApprovalExecutionNode(ApprovalStepRepository steps, NotificationService notifications) {
        this.steps = steps;
        this.notifications = notifications;
    }

    @Override
    public NodeName name() {
        return NodeName.APPROVAL_EXECUTION;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        List<ApprovalStep> live = steps.forInvoice(invoice.id()).stream()
                .filter(s -> !"VOID".equals(s.status())).toList();
        if (live.isEmpty()) {
            return StageResult.block(HoldQueue.ROUTING_ERROR, "NO_APPROVAL_STEPS", "No approval steps to execute");
        }

        Optional<ApprovalStep> current = AuthorizationService.currentStep(live);
        if (current.isEmpty()) {
            boolean allApproved = live.stream().allMatch(ApprovalStep::isApproved);
            return allApproved
                    ? StageResult.pass("All " + live.size() + " approval step(s) approved")
                    : StageResult.waitForHuman("No open step, but not all approved");
        }

        ApprovalStep step = current.get();
        notifications.notify(step.assignedUserId(), "approval_needed", invoice.id(),
                invoice.id() + " needs your approval (step " + step.stepNo() + ")");
        return StageResult.waitForHuman("Waiting for step " + step.stepNo() + ": " + step.assignedUserId());
    }
}
