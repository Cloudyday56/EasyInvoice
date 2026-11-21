package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.model.Invoice;
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

        List<NodeProgress> result = new ArrayList<>();
        for (NodeName node : NodeName.values()) {
            AuditEvent last = lastByNode.get(node.name());
            String state;
            String message = null;
            if (last == null) {
                state = node.name().equals(invoice.getCurrentNode()) ? "running" : "pending";
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

    private static String messageOf(AuditEvent event, ObjectMapper json) {
        if (event.getPayloadJson() == null) return null;
        Object message = json.readValue(event.getPayloadJson(), Map.class).get("message");
        return message == null ? null : message.toString();
    }
}
