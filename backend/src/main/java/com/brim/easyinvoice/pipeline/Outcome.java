package com.brim.easyinvoice.pipeline;

public enum Outcome {
    PASS,    // job done, go to the next node
    FLAG,    // job done with warnings; flags travel with the invoice, go to the next node
    BLOCK,   // stop: park the invoice in an exception queue until an Admin acts
    WAIT,    // nothing to do until something happens (a human decision, or the bank settling)
    IGNORE   // the document is not an invoice: end the line as IGNORED
}
