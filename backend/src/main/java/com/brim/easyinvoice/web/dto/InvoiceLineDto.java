package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.InvoiceLine;

import java.math.BigDecimal;

public record InvoiceLineDto(
        Long id,
        String invoiceId,
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

    public static InvoiceLineDto from(InvoiceLine l) {
        return new InvoiceLineDto(l.getId(), l.getInvoice().getId(), l.getLineNo(), l.getDescription(),
                l.getQuantity(), l.getUnitPrice(), l.getAmount(),
                l.getGlAccount() == null ? null : l.getGlAccount().getAccountNumber(),
                l.getDepartment() == null ? null : l.getDepartment().getId(),
                l.getGstHstCode(), l.getPstQstCode(), l.getLineType());
    }
}
