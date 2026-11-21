package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** The invoice as the UI sees it: related rows appear as ids, never as nested entities. */
public record InvoiceDto(
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
        String poNumberRaw,
        String poId,
        String contractId,
        String shipToProvince,
        Integer riskScore,
        InvoiceStatus status,
        String sourceStatus,
        String holdQueue,
        String holdReason,
        String resumeNode,
        String currentNode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static InvoiceDto from(Invoice i) {
        return new InvoiceDto(i.getId(), i.getSourceDocumentId(), i.getDocumentType().name(), i.getVendorId(),
                i.getVendorNameRaw(), i.getVendorInvoiceNumber(), i.getCanonicalInvoiceNumber(), i.getInvoiceDate(),
                i.getReceivedDate(), i.getDueDate(), i.getCurrency(), i.getSubtotal(), i.getGstHstAmount(),
                i.getPstQstAmount(), i.getTotal(), i.getFxRateToCad(), i.getTotalCad(), i.getDepartmentId(),
                i.getRequesterId(), i.getPoNumberRaw(),
                i.getPurchaseOrder() == null ? null : i.getPurchaseOrder().getId(),
                i.getContract() == null ? null : i.getContract().getId(), i.getShipToProvince(), i.getRiskScore(),
                i.getStatus(), i.getSourceStatus(), i.getHoldQueue(), i.getHoldReason(), i.getResumeNode(),
                i.getCurrentNode(), i.getCreatedAt(), i.getUpdatedAt());
    }
}
