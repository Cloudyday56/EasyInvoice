package com.brim.easyinvoice.security;

import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the pure rules in AuthorizationService.decide(): no database, no Spring. */
class AuthorizationServiceTest {

    // ---- people (department D01 unless noted) ----
    static final User EMPLOYEE = user("E1", Role.EMPLOYEE, "D01", 0, 0, "active");
    static final User APPROVER = user("A1", Role.APPROVER, "D01", 10_000, 0, "active");
    static final User OTHER_DEPT_APPROVER = user("A2", Role.APPROVER, "D02", 10_000, 0, "active");
    static final User ADMIN = user("ADM1", Role.ADMIN, "D04", 50_000, 100_000, "active");
    static final User SECOND_ADMIN = user("ADM2", Role.ADMIN, "D04", 250_000, 250_000, "active");
    static final User AUDITOR = readOnlyAdmin("AUD");
    static final User TERMINATED_ADMIN = user("ADM9", Role.ADMIN, "D04", 50_000, 100_000, "terminated");

    @Nested
    class Approve {

        @Test
        void assignedApproverWithinLimitCanApprove() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 2_000);
            assertThat(decide(APPROVER, Action.APPROVE_INVOICE, invoice, pendingStepFor("A1")).allowed()).isTrue();
        }

        @Test
        void employeeCannotApprove() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E2", "D01", 2_000);
            assertThat(decide(EMPLOYEE, Action.APPROVE_INVOICE, invoice, pendingStepFor("E1")).allowed()).isFalse();
        }

        @Test
        void noSelfApprovalEvenIfAssigned() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "A1", "D01", 2_000); // A1 requested it
            Decision d = decide(APPROVER, Action.APPROVE_INVOICE, invoice, pendingStepFor("A1"));
            assertThat(d.allowed()).isFalse();
            assertThat(d.reason()).contains("self-approval");
        }

        @Test
        void onlyTheAssignedApproverCanApprove() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 2_000);
            assertThat(decide(ADMIN, Action.APPROVE_INVOICE, invoice, pendingStepFor("A1")).allowed()).isFalse();
        }

        @Test
        void amountAboveApprovalLimitIsRefused() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 10_000.01);
            Decision d = decide(APPROVER, Action.APPROVE_INVOICE, invoice, pendingStepFor("A1"));
            assertThat(d.allowed()).isFalse();
            assertThat(d.reason()).contains("approval limit");
        }

        @Test
        void approverCannotApproveAnotherDepartment() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 2_000);
            assertThat(decide(OTHER_DEPT_APPROVER, Action.APPROVE_INVOICE, invoice, pendingStepFor("A2")).allowed())
                    .isFalse();
        }

        @Test
        void delegateMayApproveForSomeoneOnLeaveInAnotherDepartment() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 2_000);
            List<ApprovalStep> steps = List.of(step(1, "A2", "A1", "PENDING", null)); // A2 acting for A1
            assertThat(decide(OTHER_DEPT_APPROVER, Action.APPROVE_INVOICE, invoice, steps).allowed()).isTrue();
        }

        @Test
        void onlyWhenWaitingForApproval() {
            Invoice invoice = invoice(InvoiceStatus.VALIDATING, "E1", "D01", 2_000);
            assertThat(decide(APPROVER, Action.APPROVE_INVOICE, invoice, pendingStepFor("A1")).allowed()).isFalse();
        }

        @Test
        void stepsAreDecidedInOrder() {
            Invoice invoice = invoice(InvoiceStatus.PENDING_APPROVAL, "E1", "D01", 2_000);
            List<ApprovalStep> steps = List.of(step(1, "A1", null, "PENDING", null),
                    step(2, "ADM1", null, "PENDING", null));
            assertThat(decide(ADMIN, Action.APPROVE_INVOICE, invoice, steps).allowed()).isFalse(); // step 1 first
        }
    }

    @Nested
    class Release {

        @Test
        void adminWhoDidNotApproveCanRelease() {
            Invoice invoice = invoice(InvoiceStatus.SCHEDULED, "E1", "D01", 2_000);
            List<ApprovalStep> steps = List.of(step(1, "ADM1", null, "APPROVED", "ADM1"));
            assertThat(decide(SECOND_ADMIN, Action.RELEASE_PAYMENT, invoice, steps).allowed()).isTrue();
        }

        @Test
        void approverCannotReleaseTheSameInvoice() {
            Invoice invoice = invoice(InvoiceStatus.SCHEDULED, "E1", "D01", 2_000);
            List<ApprovalStep> steps = List.of(step(1, "ADM1", null, "APPROVED", "ADM1"));
            Decision d = decide(ADMIN, Action.RELEASE_PAYMENT, invoice, steps);
            assertThat(d.allowed()).isFalse();
            assertThat(d.reason()).contains("Approver cannot release");
        }

        @Test
        void nonAdminCannotRelease() {
            Invoice invoice = invoice(InvoiceStatus.SCHEDULED, "E1", "D01", 2_000);
            assertThat(decide(APPROVER, Action.RELEASE_PAYMENT, invoice, List.of()).allowed()).isFalse();
        }

        @Test
        void amountAboveReleaseLimitIsRefused() {
            Invoice invoice = invoice(InvoiceStatus.SCHEDULED, "E1", "D01", 150_000);
            assertThat(decide(ADMIN, Action.RELEASE_PAYMENT, invoice, List.of()).allowed()).isFalse();
            assertThat(decide(SECOND_ADMIN, Action.RELEASE_PAYMENT, invoice, List.of()).allowed()).isTrue();
        }

        @Test
        void onlyScheduledPaymentsCanBeReleased() {
            Invoice invoice = invoice(InvoiceStatus.APPROVED, "E1", "D01", 2_000);
            assertThat(decide(SECOND_ADMIN, Action.RELEASE_PAYMENT, invoice, List.of()).allowed()).isFalse();
        }
    }

    @Nested
    class ScopeAndStatus {

        @Test
        void employeeSeesOnlyOwnInvoices() {
            assertThat(decide(EMPLOYEE, Action.VIEW_INVOICE, invoice(InvoiceStatus.PAID, "E1", "D01", 1), List.of())
                    .allowed()).isTrue();
            assertThat(decide(EMPLOYEE, Action.VIEW_INVOICE, invoice(InvoiceStatus.PAID, "E2", "D01", 1), List.of())
                    .allowed()).isFalse();
        }

        @Test
        void approverSeesOwnDepartmentOnly() {
            Invoice d01 = invoice(InvoiceStatus.PAID, "E1", "D01", 1);
            assertThat(decide(APPROVER, Action.VIEW_INVOICE, d01, List.of()).allowed()).isTrue();
            assertThat(decide(OTHER_DEPT_APPROVER, Action.VIEW_INVOICE, d01, List.of()).allowed()).isFalse();
        }

        @Test
        void adminSeesEverything() {
            assertThat(decide(ADMIN, Action.VIEW_INVOICE, invoice(InvoiceStatus.PAID, "E1", "D07", 1), List.of())
                    .allowed()).isTrue();
        }

        @Test
        void auditorCanLookButNotTouch() {
            Invoice invoice = invoice(InvoiceStatus.SCHEDULED, "E1", "D01", 2_000);
            assertThat(decide(AUDITOR, Action.VIEW_INVOICE, invoice, List.of()).allowed()).isTrue();
            assertThat(decide(AUDITOR, Action.RELEASE_PAYMENT, invoice, List.of()).allowed()).isFalse();
            assertThat(decide(AUDITOR, Action.RUN_ADMIN_TOOLS, null, List.of()).allowed()).isFalse();
        }

        @Test
        void terminatedUserCanDoNothing() {
            Invoice invoice = invoice(InvoiceStatus.PAID, "E1", "D01", 1);
            assertThat(decide(TERMINATED_ADMIN, Action.VIEW_INVOICE, invoice, List.of()).allowed()).isFalse();
        }

        @Test
        void onlyAdminsWorkExceptionQueues() {
            Invoice held = invoice(InvoiceStatus.ON_HOLD, "E1", "D01", 1);
            assertThat(decide(ADMIN, Action.RESOLVE_HOLD, held, List.of()).allowed()).isTrue();
            assertThat(decide(APPROVER, Action.RESOLVE_HOLD, held, List.of()).allowed()).isFalse();
        }

        @Test
        void approversCannotUpload() {
            assertThat(decide(APPROVER, Action.UPLOAD_INVOICE, null, List.of()).allowed()).isFalse();
            assertThat(decide(EMPLOYEE, Action.UPLOAD_INVOICE, null, List.of()).allowed()).isTrue();
        }
    }

    // ---------- helpers ----------

    private static Decision decide(User user, Action action, Invoice invoice, List<ApprovalStep> steps) {
        return AuthorizationService.decide(user, action, invoice, steps);
    }

    private static User user(String id, Role role, String dept, double approvalLimit, double releaseLimit,
                             String status) {
        User user = new User();
        user.setId(id);
        user.setFullName(id);
        user.setRole(role);
        user.setDepartmentId(dept);
        user.setApprovalLimitCad(BigDecimal.valueOf(approvalLimit));
        user.setReleaseLimitCad(BigDecimal.valueOf(releaseLimit));
        user.setStatus(status);
        return user;
    }

    private static User readOnlyAdmin(String id) {
        User user = user(id, Role.ADMIN, "D04", 0, 0, "active");
        user.setSourceRole("auditor");
        user.setReadOnly(true);
        return user;
    }

    private static Invoice invoice(InvoiceStatus status, String requesterId, String dept, double totalCad) {
        Invoice invoice = new Invoice();
        invoice.setId("INV-1");
        invoice.setVendorId("V001");
        invoice.setCurrency("CAD");
        invoice.setTotal(BigDecimal.valueOf(totalCad));
        invoice.setTotalCad(BigDecimal.valueOf(totalCad));
        invoice.setDepartmentId(dept);
        invoice.setRequesterUserId(requesterId);
        invoice.setStatus(status);
        return invoice;
    }

    private static ApprovalStep step(int no, String assignee, String actingFor, String status, String actedBy) {
        ApprovalStep step = new ApprovalStep();
        step.setInvoiceId("INV-1");
        step.setStepNo(no);
        step.setRequiredRole("APPROVER");
        step.setAssignedUserId(assignee);
        step.setActingFor(actingFor);
        step.setStatus(status);
        step.setActedBy(actedBy);
        return step;
    }

    private static List<ApprovalStep> pendingStepFor(String assignee) {
        return List.of(step(1, assignee, null, "PENDING", null));
    }
}
