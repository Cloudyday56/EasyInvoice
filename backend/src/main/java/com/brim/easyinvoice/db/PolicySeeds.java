package com.brim.easyinvoice.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * approval_policies.csv writes its conditions in plain English, so they cannot be loaded mechanically.
 * This is our hand translation into JSON rules, with the original text kept next to each one.
 *
 * Rules with "add_step" are meant for the Approval routing policy engine (TODO: evaluate them).
 * Rules with "enforced_in" are behaviours implemented in code, listed here so the table is complete.
 */
final class PolicySeeds {

    private PolicySeeds() {
    }

    private record Policy(String id, String ruleType, String json, String sourceText) {
    }

    private static final List<Policy> POLICIES = List.of(
            new Policy("P01", "approval",
                    "{\"always\": true, \"add_step\": \"DEPARTMENT_APPROVER\"}",
                    "Invoice total up to $10,000 CAD -> dept_manager"),
            new Policy("P02", "approval",
                    "{\"if\": {\"field\": \"total_cad\", \"op\": \">\", \"value\": 10000}, \"add_step\": \"ADMIN\", \"note\": \"finance manager\"}",
                    "Invoice total over $10,000 CAD -> dept_manager > finance_manager"),
            new Policy("P03", "approval",
                    "{\"if\": {\"field\": \"total_cad\", \"op\": \">\", \"value\": 50000}, \"add_step\": \"ADMIN\", \"note\": \"controller\"}",
                    "Invoice total over $50,000 CAD -> ... > controller_admin"),
            new Policy("P04", "approval",
                    "{\"if\": {\"field\": \"total_cad\", \"op\": \">\", \"value\": 250000}, \"add_step\": \"ADMIN\", \"note\": \"CFO\"}",
                    "Invoice total over $250,000 CAD -> ... > cfo"),
            new Policy("P05", "approval",
                    "{\"if\": {\"field\": \"vendor.status\", \"op\": \"in\", \"value\": [\"pending_review\", \"new\"]}, \"add_step\": \"ADMIN\"}",
                    "Vendor status is pending_review or the vendor is new -> finance_manager"),
            new Policy("P06", "approval",
                    "{\"if\": {\"field\": \"vendor.category\", \"op\": \"==\", \"value\": \"Legal\"}, \"add_step\": \"ADMIN\"}",
                    "Vendor category is Legal -> finance_manager"),
            new Policy("P07", "approval",
                    "{\"enforced_in\": \"ApprovalRoutingNode.resolve + AuthorizationService (no self-approval)\"}",
                    "Requester is the approver -> approver's manager"),
            new Policy("P08", "approval",
                    "{\"enforced_in\": \"ApprovalRoutingNode.resolve (delegate + acting_for)\"}",
                    "Approver is on leave -> delegate_user_id"),
            new Policy("P09", "payment_release",
                    "{\"enforced_in\": \"users.release_limit_cad = 100000 for finance_manager; AuthorizationService\"}",
                    "Payment up to $100,000 CAD -> finance_manager (AP clerks prepare, cannot release)"),
            new Policy("P10", "payment_release",
                    "{\"enforced_in\": \"users.release_limit_cad = approval limit for controller_admin; AuthorizationService\"}",
                    "Payment over $100,000 CAD -> controller_admin"),
            new Policy("P11", "payment_release",
                    "{\"enforced_in\": \"AuthorizationService (approver != releaser)\"}",
                    "Always -> not the invoice approver"),
            new Policy("P12", "match",
                    "{\"enforced_in\": \"ValidationRulesNode (TODO: PO balance check)\"}",
                    "Invoice references a PO -> total within PO remaining balance"),
            new Policy("P13", "match",
                    "{\"enforced_in\": \"ValidationRulesNode (TODO: vendors.requires_po)\"}",
                    "Vendor category requires a PO -> PO number present"));

    /** The policies as rows for SeedLoader to insert (column name -> value). */
    static List<Map<String, Object>> rows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int order = 0;
        for (Policy p : POLICIES) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.id());
            row.put("rule_type", p.ruleType());
            row.put("rule_json", p.json());
            row.put("source_text", p.sourceText());
            row.put("sort_order", order++);
            rows.add(row);
        }
        return rows;
    }
}
