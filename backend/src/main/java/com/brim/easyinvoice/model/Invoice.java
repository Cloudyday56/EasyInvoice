package com.brim.easyinvoice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record Invoice(
        String id,
        Long sourceDocumentId,
        String documentType,
        String vendorId,
        String vendorNameRaw,
        String vendorInvoiceNumber,
        String canonicalInvoiceNumber,
        LocalDate invoiceDate,
        LocalDate receivedDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal gstHstAmount,
        BigDecimal pstQstAmount,
        BigDecimal total,
        BigDecimal fxRateToCad,
        BigDecimal totalCad,
        String departmentId,
        String requesterUserId,
        String poId,
        String contractId,
        String shipToProvince,
        Integer riskScore,
        InvoiceStatus status,
        String holdQueue,
        String holdReason,
        String resumeNode,
        String currentNode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
