package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.FlagSeverity;

/** A flag a node wants to attach. severity: FLAG (warning) or BLOCK. */
public record FlagDraft(String code, FlagSeverity severity, String message) {

    public static FlagDraft warning(String code, String message) {
        return new FlagDraft(code, FlagSeverity.FLAG, message);
    }
}
