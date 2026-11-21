package com.brim.easyinvoice.model;

import java.math.BigDecimal;

public record InvoiceLine(
        int lineNo,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String glAccount,
        String departmentId,
        String gstHstCode,
        String pstQstCode,
        String lineType) {
}
