package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.pipeline.NodeName;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the 10-step tracker on the invoice page from the invoice's audit events. */
final class PipelineProgress {

    /** state: pending | running | done | flagged | blocked | waiting | retrying | ignored */
    record NodeProgress(String node, String label, String state, String message) {
    }

    private PipelineProgress() {
    }

    static List<NodeProgress> compute(Invoice invoice, List<AuditEvent> events, ObjectMapper json) {
        // The last "node.*" event for each node tells us where that node stands.
        Map<String, AuditEvent> lastByNode = new HashMap<>();
        for (AuditEvent e : events) {
            if (e.getNode() != null && e.getAction().startsWith("node.")) {
                lastByNode.put(e.getNode(), e);
            }
        }

        // Seeded history has a final status but no node.* events. Fill in the steps its status implies.
        int inferredFloor = inferredFloor(invoice);
        List<NodeProgress> result = new ArrayList<>();
        for (NodeName node : NodeName.values()) {
            AuditEvent last = lastByNode.get(node.name());
            String state;
            String message = null;
            if (last == null) {
                int index = node.ordinal();
                if (index < inferredFloor) {
                    state = "done";
                } else if (index == inferredFloor) {
                    state = inferredState(invoice);
                    message = lastByNode.isEmpty() ? "From sample history" : null;
                } else {
                    state = "pending";
                }
            } else {
                state = switch (last.getAction()) {
                    case "node.PASS" -> "done";
                    case "node.FLAG" -> "flagged";
                    case "node.BLOCK" -> "blocked";
                    case "node.WAIT" -> "waiting";
                    case "node.RETRY" -> "retrying";
                    case "node.IGNORE" -> "ignored";
                    default -> "pending";
                };
                message = messageOf(last, json);
            }
            // Payment release re-checks the bank without logging; show it as running once released.
            if (node == NodeName.PAYMENT_RELEASE && "waiting".equals(state)
                    && invoice.getStatus().name().equals("RELEASED")) {
                state = "running";
                message = "Sent to bank, waiting for settlement";
            }
            result.add(new NodeProgress(node.name(), node.label(), state, message));
        }
        return result;
    }

    /** How many steps the invoice's status says it has passed, when events are missing. */
    private static int inferredFloor(Invoice invoice) {
        InvoiceStatus status = invoice.getStatus();
        int current = currentIndex(invoice);
        return switch (status) {
            case PAID -> NodeName.values().length;
            case RELEASED, SCHEDULED -> NodeName.PAYMENT_RELEASE.ordinal();
            case APPROVED -> NodeName.PAYMENT_SCHEDULING.ordinal();
            case PENDING_APPROVAL -> NodeName.APPROVAL_EXECUTION.ordinal();
            case ON_HOLD, ON_HOLD_VENDOR, VALIDATING -> current >= 0 ? current : NodeName.VALIDATION_RULES.ordinal();
            case EXTRACTED -> NodeName.EXTRACTION.ordinal();
            case REJECTED -> NodeName.APPROVAL_EXECUTION.ordinal();
            case IGNORED -> NodeName.EMAIL_CLASSIFIER.ordinal();
            case RECEIVED -> Math.max(0, current);
        };
    }

    private static int currentIndex(Invoice invoice) {
        if (invoice.getCurrentNode() == null) return -1;
        try {
            return NodeName.valueOf(invoice.getCurrentNode()).ordinal();
        } catch (IllegalArgumentException ignored) {
            return -1;
        }
    }

    private static String inferredState(Invoice invoice) {
        return switch (invoice.getStatus()) {
            case PAID -> "done";
            case REJECTED -> "blocked";
            case IGNORED -> "ignored";
            case ON_HOLD, ON_HOLD_VENDOR, PENDING_APPROVAL, SCHEDULED -> "waiting";
            default -> "running";
        };
    }

    private static String messageOf(AuditEvent event, ObjectMapper json) {
        if (event.getPayloadJson() == null) return null;
        Object message = json.readValue(event.getPayloadJson(), Map.class).get("message");
        return message == null ? null : message.toString();
    }
}
