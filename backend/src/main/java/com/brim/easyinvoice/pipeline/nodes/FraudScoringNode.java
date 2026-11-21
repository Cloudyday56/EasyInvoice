package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job: catch payment fraud before money moves. Weighted signals -> 0..100 risk score.
 * STUB: score 0, always passes.
 *
 * TODO signals: remit-to bank differs from vendor master; unverified bank change in last 30 days
 * (bank_change_requests); lookalike / free-mail sender domain; split invoices under a limit;
 * amount > 3 std devs from vendor history.
 * Outcomes: < 40 PASS; 40-69 FLAG (adds Admin step); >= 70 or bank mismatch -> BLOCK Fraud hold.
 */
@Component
public class FraudScoringNode implements Node {

    @Override
    public NodeName name() {
        return NodeName.FRAUD_SCORING;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        return StageResult.pass("Stub: risk score 0 (no signals implemented yet)", Map.of("riskScore", 0));
    }
}
