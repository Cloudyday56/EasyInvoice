package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
        String paymentMethod,         // method of the newest payment (EFT | CHEQUE | CARD), null before one exists
        String paymentReference,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        // Filled by the invoice list and detail endpoints (they know the user); empty elsewhere. They let the UI
        // show badges, the action button and its label from the list row, without waiting for the detail.
        List<FlagDto> flags,
        List<String> allowedActions,
        String waitingOnUserId,
        String scheduledPaymentId) {

    public static InvoiceDto from(Invoice i) {
        Payment latest = i.getPayments().isEmpty() ? null : i.getPayments().get(i.getPayments().size() - 1);
        return new InvoiceDto(i.getId(), i.getSourceDocumentId(), i.getDocumentType().name(), i.getVendorId(),
                i.getVendorNameRaw(), i.getVendorInvoiceNumber(), i.getCanonicalInvoiceNumber(), i.getInvoiceDate(),
                i.getReceivedDate(), i.getDueDate(), i.getCurrency(), i.getSubtotal(), i.getGstHstAmount(),
                i.getPstQstAmount(), i.getTotal(), i.getFxRateToCad(), i.getTotalCad(), i.getDepartmentId(),
                i.getRequesterId(), i.getPoNumberRaw(),
                i.getPurchaseOrder() == null ? null : i.getPurchaseOrder().getId(),
                i.getContract() == null ? null : i.getContract().getId(), i.getShipToProvince(), i.getRiskScore(),
                i.getStatus(), i.getSourceStatus(), i.getHoldQueue(), i.getHoldReason(), i.getResumeNode(),
                i.getCurrentNode(), latest == null ? null : latest.getMethod().name(),
                latest == null ? null : latest.getReference(), i.getCreatedAt(), i.getUpdatedAt(),
                List.of(), List.of(), null, null);
    }

    public InvoiceDto with(List<FlagDto> flags, List<String> allowedActions, String waitingOnUserId,
                           String scheduledPaymentId) {
        return new InvoiceDto(id, sourceDocumentId, documentType, vendorId, vendorNameRaw, vendorInvoiceNumber,
                canonicalInvoiceNumber, invoiceDate, receivedDate, dueDate, currency, subtotal, gstHstAmount,
                pstQstAmount, total, fxRateToCad, totalCad, departmentId, requesterUserId, poNumberRaw, poId,
                contractId, shipToProvince, riskScore, status, sourceStatus, holdQueue, holdReason, resumeNode,
                currentNode, paymentMethod, paymentReference, createdAt, updatedAt,
                flags, allowedActions, waitingOnUserId, scheduledPaymentId);
    }
}
