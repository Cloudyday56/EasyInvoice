package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import org.springframework.stereotype.Component;

/**
 * Job: prove the invoice is correct against the contract, the PO and Canadian tax rules.
 * STUB: always passes.
 *
 * TODO (each a small pure function returning a FlagDraft with a severity):
 *  - arithmetic: qty x price = amount, lines sum to subtotal, subtotal + taxes = total (BLOCK)
 *  - tax by ship-to province (ON 13%, BC 5%+7% goods, QC 5%+9.975%, AB 5%) (BLOCK); missing tax number (FLAG)
 *  - PO exists, open, same vendor, balance (<=5% over FLAG, more BLOCK); missing PO when vendor requires one (FLAG)
 *  - contract active on invoice date, rate caps, monthly not-to-exceed (FLAG)
 */
@Component
public class ValidationRulesNode implements Node {

    @Override
    public NodeName name() {
        return NodeName.VALIDATION_RULES;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        return StageResult.pass("Stub: arithmetic, tax, PO and contract checks not implemented yet");
    }
}
