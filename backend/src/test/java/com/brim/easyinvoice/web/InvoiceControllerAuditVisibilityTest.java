package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.intake.FileStorage;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.DocumentType;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.HumanActionService;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.CardTransactionRepository;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceLineRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.SourceDocumentRepository;
import com.brim.easyinvoice.repo.VirtualCardRepository;
import com.brim.easyinvoice.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The invoice page may show the pipeline, but raw audit events are admin-only. */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerAuditVisibilityTest {

    @Mock private CurrentUser currentUser;
    @Mock private InvoiceRepository invoices;
    @Mock private InvoiceLineRepository invoiceLines;
    @Mock private FlagRepository flags;
    @Mock private ApprovalStepRepository steps;
    @Mock private PaymentRepository payments;
    @Mock private VirtualCardRepository virtualCards;
    @Mock private CardTransactionRepository cardTransactions;
    @Mock private SourceDocumentRepository documents;
    @Mock private AuditService audit;
    @Mock private IntakeService intake;
    @Mock private HumanActionService actions;
    @Mock private FileStorage storage;

    private InvoiceController controller;

    @BeforeEach
    void setUp() {
        controller = new InvoiceController(currentUser, invoices, invoiceLines, flags, steps, payments,
                virtualCards, cardTransactions, documents,
                new AuthorizationService(steps, audit), audit, intake, actions, storage, null);

        when(invoices.get("INV-1")).thenReturn(invoice());
        when(steps.forInvoice("INV-1")).thenReturn(List.of());
        when(invoiceLines.findByInvoice_IdOrderByLineNo("INV-1")).thenReturn(List.of());
        when(flags.findByInvoice_IdOrderById("INV-1")).thenReturn(List.of());
        when(payments.findByInvoice_IdOrderById("INV-1")).thenReturn(List.of());
        when(audit.forInvoice("INV-1")).thenReturn(List.of(
                AuditEvent.builder().id(1L).action("user.upload").build()));
    }

    @Test
    void adminReceivesRawAuditEvents() {
        when(currentUser.require("U001")).thenReturn(user("U001", Role.ADMIN));

        assertThat(controller.detail("U001", "INV-1").auditEvents()).hasSize(1);
    }

    @Test
    void nonAdminDoesNotReceiveRawAuditEvents() {
        when(currentUser.require("U5")).thenReturn(user("U5", Role.EMPLOYEE));

        assertThat(controller.detail("U5", "INV-1").auditEvents()).isEmpty();
    }

    private static User user(String id, Role role) {
        User user = new User();
        user.setId(id);
        user.setFullName(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static Invoice invoice() {
        Vendor vendor = new Vendor();
        vendor.setId("V1");
        vendor.setLegalName("Acme Corp");

        Invoice invoice = new Invoice();
        invoice.setId("INV-1");
        invoice.setDocumentType(DocumentType.INVOICE);
        invoice.setVendor(vendor);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setRequester(user("U5", Role.EMPLOYEE));
        return invoice;
    }
}
