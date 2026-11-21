package com.brim.easyinvoice.pipeline;

/** A flag a node wants to attach. severity: "FLAG" (warning) or "BLOCK". */
public record FlagDraft(String code, String severity, String message) {

    public static FlagDraft warning(String code, String message) {
        return new FlagDraft(code, "FLAG", message);
    }
}
